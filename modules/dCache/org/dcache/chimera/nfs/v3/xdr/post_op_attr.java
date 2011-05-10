/*
 * Automatically generated by jrpcgen 1.0.7 on 2/21/09 1:22 AM
 * jrpcgen is part of the "Remote Tea" ONC/RPC package for Java
 * See http://remotetea.sourceforge.net for details
 */
package org.dcache.chimera.nfs.v3.xdr;
import org.dcache.xdr.*;
import java.io.IOException;

public class post_op_attr implements XdrAble {
    public boolean attributes_follow;
    public fattr3 attributes;

    public post_op_attr() {
    }

    public post_op_attr(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        xdrDecode(xdr);
    }

    public void xdrEncode(XdrEncodingStream xdr)
           throws OncRpcException, IOException {
        xdr.xdrEncodeBoolean(attributes_follow);
        if ( attributes_follow ) {
            attributes.xdrEncode(xdr);
        }
    }

    public void xdrDecode(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        attributes_follow = xdr.xdrDecodeBoolean();
        if ( attributes_follow ) {
            attributes = new fattr3(xdr);
        }
    }

}
// End of post_op_attr.java
