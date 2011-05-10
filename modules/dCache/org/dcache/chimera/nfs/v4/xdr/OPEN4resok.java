/*
 * Automatically generated by jrpcgen 1.0.7 on 2/21/09 1:22 AM
 * jrpcgen is part of the "Remote Tea" ONC/RPC package for Java
 * See http://remotetea.sourceforge.net for details
 */
package org.dcache.chimera.nfs.v4.xdr;
import org.dcache.xdr.*;
import java.io.IOException;

public class OPEN4resok implements XdrAble {
    public stateid4 stateid;
    public change_info4 cinfo;
    public uint32_t rflags;
    public bitmap4 attrset;
    public open_delegation4 delegation;

    public OPEN4resok() {
    }

    public OPEN4resok(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        xdrDecode(xdr);
    }

    public void xdrEncode(XdrEncodingStream xdr)
           throws OncRpcException, IOException {
        stateid.xdrEncode(xdr);
        cinfo.xdrEncode(xdr);
        rflags.xdrEncode(xdr);
        attrset.xdrEncode(xdr);
        delegation.xdrEncode(xdr);
    }

    public void xdrDecode(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        stateid = new stateid4(xdr);
        cinfo = new change_info4(xdr);
        rflags = new uint32_t(xdr);
        attrset = new bitmap4(xdr);
        delegation = new open_delegation4(xdr);
    }

}
// End of OPEN4resok.java
