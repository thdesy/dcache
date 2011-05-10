/*
 * Automatically generated by jrpcgen 1.0.7 on 2/21/09 1:22 AM
 * jrpcgen is part of the "Remote Tea" ONC/RPC package for Java
 * See http://remotetea.sourceforge.net for details
 */
package org.dcache.chimera.nfs.v4.xdr;
import org.dcache.xdr.*;
import java.io.IOException;

public class SET_SSV4args implements XdrAble {
    public byte [] ssa_ssv;
    public byte [] ssa_digest;

    public SET_SSV4args() {
    }

    public SET_SSV4args(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        xdrDecode(xdr);
    }

    public void xdrEncode(XdrEncodingStream xdr)
           throws OncRpcException, IOException {
        xdr.xdrEncodeDynamicOpaque(ssa_ssv);
        xdr.xdrEncodeDynamicOpaque(ssa_digest);
    }

    public void xdrDecode(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        ssa_ssv = xdr.xdrDecodeDynamicOpaque();
        ssa_digest = xdr.xdrDecodeDynamicOpaque();
    }

}
// End of SET_SSV4args.java
