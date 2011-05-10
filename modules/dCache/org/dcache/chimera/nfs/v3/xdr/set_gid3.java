/*
 * Automatically generated by jrpcgen 1.0.7 on 2/21/09 1:22 AM
 * jrpcgen is part of the "Remote Tea" ONC/RPC package for Java
 * See http://remotetea.sourceforge.net for details
 */
package org.dcache.chimera.nfs.v3.xdr;
import org.dcache.xdr.*;
import java.io.IOException;

public class set_gid3 implements XdrAble {
    public boolean set_it;
    public gid3 gid;

    public set_gid3() {
    }

    public set_gid3(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        xdrDecode(xdr);
    }

    public void xdrEncode(XdrEncodingStream xdr)
           throws OncRpcException, IOException {
        xdr.xdrEncodeBoolean(set_it);
        if ( set_it ) {
            gid.xdrEncode(xdr);
        }
    }

    public void xdrDecode(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        set_it = xdr.xdrDecodeBoolean();
        if ( set_it ) {
            gid = new gid3(xdr);
        }
    }

}
// End of set_gid3.java
