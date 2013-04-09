/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2014 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.webdav.transfer;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import io.milton.http.Response;
import io.milton.http.Response.Status;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.globus.gsi.X509Credential;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.security.auth.Subject;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Set;

import diskCacheV111.services.TransferManagerHandler;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.TimeoutCacheException;
import diskCacheV111.vehicles.IoJobInfo;
import diskCacheV111.vehicles.IpProtocolInfo;
import diskCacheV111.vehicles.transferManager.CancelTransferMessage;
import diskCacheV111.vehicles.transferManager.RemoteGsiftpTransferProtocolInfo;
import diskCacheV111.vehicles.transferManager.RemoteTransferManagerMessage;
import diskCacheV111.vehicles.transferManager.TransferCompleteMessage;
import diskCacheV111.vehicles.transferManager.TransferFailedMessage;
import diskCacheV111.vehicles.transferManager.TransferStatusQueryMessage;

import dmg.cells.nucleus.CellMessageReceiver;

import org.dcache.cells.CellStub;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * This class provides the basis for interactions with the remotetransfer
 * service.  It is used by the CopyFilter to manage a requested transfer and
 * to provide feedback on that transfer in the form of performance markers.
 * <p>
 * The performance markers are similar to those provided during an FTP
 * transfer.  They have the form:
 * <code>
 *     Perf Marker
 *      Timestamp: 1360578938
 *      Stripe Index: 0
 *      Stripe Bytes Transferred: 49397760
 *      Total Stripe Count: 2
 *     End
 * </code>
 *
 * Once the transfer has completed successfully, {@code success: Created} is
 * reported.  On failure {@code failure: <explanation>} is returned.
 * <p>
 * Although the third-party transfer protocol, described in CopyFilter is
 * documented as supporting 'https' URIs, this implementation supports
 * different transports for the third-party transfer.
 */
public class RemoteTransferHandler implements CellMessageReceiver
{
    /**
     * The different transport schemes supported.
     */
    public enum TransferType {
        GSIFTP(2811), HTTPS(443);

        private static final ImmutableMap<String,TransferType> BY_NAME =
            ImmutableMap.of("gsiftp", GSIFTP, "https", HTTPS);

        private final int _defaultPort;

        TransferType(int port) {
            _defaultPort = port;
        }

        public int getDefaultPort() {
            return _defaultPort;
        }

        public static TransferType fromScheme(String scheme)
        {
            return BY_NAME.get(scheme.toLowerCase());
        }

        public static Set<String> validSchemes()
        {
            return BY_NAME.keySet();
        }

        public static boolean isSchemeSupported(String scheme)
        {
            return fromScheme(scheme) != null;
        }
    };

    private static final Logger LOG =
            LoggerFactory.getLogger(RemoteTransferHandler.class);
    private static final long DUMMY_LONG = 0;

    private final HashMap<Long,RemoteTransfer> _transfers = new HashMap<>();

    private long _performanceMarkerPeriod;
    private CellStub _transferManager;


    @Required
    public void setTransferManagerStub(CellStub stub)
    {
        _transferManager = stub;
    }

    @Required
    public void setPerformanceMarkerPeroid(long peroid)
    {
        _performanceMarkerPeriod = peroid;
    }

    public long getPerformanceMarkerPeroid()
    {
        return _performanceMarkerPeriod;
    }

    public void acceptRequest(OutputStream out, Subject subject, FsPath path,
            URI destination, X509Credential credential)
            throws ErrorResponseException, InterruptedException
    {
        RemoteTransfer transfer = new RemoteTransfer(out, subject, path, destination, credential);

        long id;

        synchronized (_transfers) {
            id = transfer.start();
            _transfers.put(id, transfer);
        }

        try {
            transfer.awaitCompletion();
        } finally {
            synchronized (_transfers) {
                _transfers.remove(id);
            }
        }
    }


    public void messageArrived(TransferCompleteMessage message)
    {
        synchronized (_transfers) {
            RemoteTransfer transfer = _transfers.get(message.getId());
            if (transfer != null) {
                transfer.success();
            }
        }
    }

    public void messageArrived(TransferFailedMessage message)
    {
        synchronized (_transfers) {
            RemoteTransfer transfer = _transfers.get(message.getId());
            if (transfer != null) {
                transfer.failure(String.valueOf(message.getErrorObject()));
            }
        }
    }

    /**
     * Class that represents a client's request to transfer a file to some
     * remote server.
     * <p>
     * This class needs to be aware of the client closing its end of the TCP
     * connection while the transfer underway.  In the protocol, this is used
     * to indicate that the transfer should be cancelled.  Unfortunately, there
     * is no container-independent mechanism for discovering this, so
     * Jetty-specific code is needed.
     */
    private class RemoteTransfer
    {
        private final TransferType _type;
        private final Subject _subject;
        private final FsPath _path;
        private final URI _destination;
        private final X509Credential _credential;
        private final PrintWriter _out;
        private String _problem;
        private long _id;
        private final EndPoint _endpoint = AbstractHttpConnection.
                getCurrentConnection().getEndPoint();

        private boolean _finished;

        public RemoteTransfer(OutputStream out, Subject subject, FsPath path,
                URI destination, X509Credential credential)
        {
            _subject = subject;
            _path = path;
            _destination = destination;
            _type = TransferType.fromScheme(destination.getScheme());
            _credential = credential;
            _out = new PrintWriter(out);
        }

        private long start() throws ErrorResponseException, InterruptedException
        {
            RemoteTransferManagerMessage message =
                    new RemoteTransferManagerMessage(_destination, _path, false,
                            DUMMY_LONG, buildProtocolInfo());

            message.setSubject(_subject);

            try {
                _id = _transferManager.sendAndWait(message).getId();
                return _id;
            } catch (TimeoutCacheException e) {
                LOG.error("Failed to send request to transfer manager: {}", e.getMessage());
                throw new ErrorResponseException(Response.Status.SC_INTERNAL_SERVER_ERROR,
                        "transfer service unavailable");
            } catch (CacheException e) {
                LOG.error("Error from transfer manager: {}", e.getMessage());
                throw new ErrorResponseException(Response.Status.SC_INTERNAL_SERVER_ERROR,
                        "transfer not accepted: " + e.getMessage());
            }
        }

        /**
         * Check that the client is still connected.  To be effective, the
         * Connector should make use of NIO (e.g., SelectChannelConnector or
         * SslSelectChannelConnector) and this method should be called after
         * output has been written to the client.
        */
        private void checkClientConnected()
        {
            if (!_endpoint.isOpen()) {
                CancelTransferMessage message =
                        new CancelTransferMessage(_id, DUMMY_LONG);
                message.setExplanation("client went away");
                try {
                    _transferManager.sendAndWait(message);
                } catch (CacheException e) {
                    LOG.error("Failed to cancel transfer id={}: {}", _id, e.toString());

                    // Our attempt to kill the transfer failed.  We leave the
                    // performance markers going as they will trigger further
                    // attempts to kill the transfer.
                } catch (InterruptedException e) {
                    // Do nothing: this dCache domain is shutting down.
                }
            }
        }

        private IpProtocolInfo buildProtocolInfo() throws ErrorResponseException
        {
            int buffer = 1024*1024;

            int port = _destination.getPort();
            if (port == -1) {
                port = _type.getDefaultPort();
            }

            InetSocketAddress address = new InetSocketAddress(_destination.getHost(), port);

            switch (_type) {
            case GSIFTP:
                try {
                    return new RemoteGsiftpTransferProtocolInfo("RemoteGsiftpTransfer",
                            1, 1, address, _destination.toASCIIString(), null,
                            null, buffer, 1024*1024,
                            new GlobusGSSCredentialImpl(_credential,
                                    GSSCredential.INITIATE_AND_ACCEPT));
                } catch (GSSException e) {
                    LOG.error("Failed to create ProtocolInfo: {}", e.getMessage());
                    throw new ErrorResponseException(Status.SC_INTERNAL_SERVER_ERROR,
                            "Problem using delegated credential");
                }
            case HTTPS:
                // FIXME when third-party HTTPS support is available.
                throw new ErrorResponseException(Status.SC_BAD_REQUEST,
                        "The https transport is currently not available");
            }

            throw new RuntimeException("Unexpected TransferType: " + _type);
        }

        public synchronized void success()
        {
            _problem = null;
            _finished = true;
            notifyAll();
        }

        public synchronized void failure(String explanation)
        {
            _problem = explanation;
            _finished = true;
            notifyAll();
        }

        public synchronized void awaitCompletion() throws InterruptedException
        {
            do {
                generateMarker();

                wait(_performanceMarkerPeriod);
            } while (!_finished);

            if (_problem == null) {
                _out.println("success: Created");
            } else {
                _out.println("failure: " + _problem);
            }
            _out.flush();
        }

        private void generateMarker() throws InterruptedException
        {
            TransferStatusQueryMessage message =
                    new TransferStatusQueryMessage(_id);
            ListenableFuture<TransferStatusQueryMessage> future =
                    _transferManager.send(message, _performanceMarkerPeriod/2);

            try {
                TransferStatusQueryMessage reply = CellStub.getMessage(future);

                if (!_finished) {
                    sendMarker(reply.getState(),
                            reply.getMoverInfo());
                    checkClientConnected();
                }
            } catch (CacheException e) {
                LOG.warn("Failed to fetch information for progress marker: {}",
                        e.getMessage());
            }
       }


        /**
         * Print a performance marker on the reply channel that looks something
         * like:
         *
         *     Perf Marker
         *      Timestamp: 1360578938
         *      Stripe Index: 0
         *      Stripe Bytes Transferred: 49397760
         *      Total Stripe Count: 2
         *     End
         *
         */
        public void sendMarker(int state, IoJobInfo info)
        {
            _out.println("Perf Marker");
            _out.println("    Timestamp: " +
                    MILLISECONDS.toSeconds(System.currentTimeMillis()));
            _out.println("    State: " + state);
            _out.println("    State description: " + descriptionForState(state));
            _out.println("    Stripe Index: 0");
            if (info != null) {
                _out.println("    Stripe Start Time: " +
                        MILLISECONDS.toSeconds(info.getStartTime()));
                _out.println("    Stripe Last Transferred: " +
                        MILLISECONDS.toSeconds(info.getLastTransferred()));
                _out.println("    Stripe Transfer Time: " +
                        MILLISECONDS.toSeconds(info.getTransferTime()));
                _out.println("    Stripe Bytes Transferred: " +
                        info.getBytesTransferred());
                _out.println("    Stripe Status: " + info.getStatus());
            }
            _out.println("    Total Stripe Count: 1");
            _out.println("End");
            _out.flush();
        }

        private String descriptionForState(int state)
        {
            switch (state) {
            case TransferManagerHandler.INITIAL_STATE:
                return "initialising";
            case TransferManagerHandler.WAITING_FOR_PNFS_INFO_STATE:
                return "querying file metadata";
            case TransferManagerHandler.RECEIVED_PNFS_INFO_STATE:
                return "recieved file metadata";
            case TransferManagerHandler.WAITING_FOR_PNFS_ENTRY_CREATION_INFO_STATE:
                return "creating empty local file";
            case TransferManagerHandler.RECEIVED_PNFS_ENTRY_CREATION_INFO_STATE:
                return "empty local file created";
            case TransferManagerHandler.WAITING_FOR_POOL_INFO_STATE:
                return "selecting pool";
            case TransferManagerHandler.RECEIVED_POOL_INFO_STATE:
                return "pool selected";
            case TransferManagerHandler.WAITING_FIRST_POOL_REPLY_STATE:
                return "waiting for transfer to start";
            case TransferManagerHandler.RECEIVED_FIRST_POOL_REPLY_STATE:
                return "transfer has started";
            case TransferManagerHandler.WAITING_FOR_SPACE_INFO_STATE:
                return "reserving space";
            case TransferManagerHandler.RECEIVED_SPACE_INFO_STATE:
                return "space reserved";
            case TransferManagerHandler.WAITING_FOR_PNFS_ENTRY_DELETE:
                return "requesting file deletion";
            case TransferManagerHandler.RECEIVED_PNFS_ENTRY_DELETE:
                return "notified of file deletion";
            case TransferManagerHandler.WAITING_FOR_PNFS_CHECK_BEFORE_DELETE_STATE:
                return "checking before file deletion";
            case TransferManagerHandler.RECEIVED_PNFS_CHECK_BEFORE_DELETE_STATE:
                return "confirmed file deletion OK";
            case TransferManagerHandler.SENT_ERROR_REPLY_STATE:
                return "transfer failed";
            case TransferManagerHandler.SENT_SUCCESS_REPLY_STATE:
                return "transfer succeeded";
            case TransferManagerHandler.UNKNOWN_ID:
                return "unknown transfer";
            default:
                return "unknown state";
            }
        }
    }
}
