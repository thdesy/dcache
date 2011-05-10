/*
 * Automatically generated by jrpcgen 1.0.7 on 2/21/09 1:22 AM
 * jrpcgen is part of the "Remote Tea" ONC/RPC package for Java
 * See http://remotetea.sourceforge.net for details
 */
package org.dcache.chimera.nfs.v4.xdr;
import org.dcache.xdr.*;
import java.io.IOException;

public class ssv_seal_cipher_tkn4 implements XdrAble {
    public uint32_t ssct_ssv_seq;
    public byte [] ssct_iv;
    public byte [] ssct_encr_data;
    public byte [] ssct_hmac;

    public ssv_seal_cipher_tkn4() {
    }

    public ssv_seal_cipher_tkn4(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        xdrDecode(xdr);
    }

    public void xdrEncode(XdrEncodingStream xdr)
           throws OncRpcException, IOException {
        ssct_ssv_seq.xdrEncode(xdr);
        xdr.xdrEncodeDynamicOpaque(ssct_iv);
        xdr.xdrEncodeDynamicOpaque(ssct_encr_data);
        xdr.xdrEncodeDynamicOpaque(ssct_hmac);
    }

    public void xdrDecode(XdrDecodingStream xdr)
           throws OncRpcException, IOException {
        ssct_ssv_seq = new uint32_t(xdr);
        ssct_iv = xdr.xdrDecodeDynamicOpaque();
        ssct_encr_data = xdr.xdrDecodeDynamicOpaque();
        ssct_hmac = xdr.xdrDecodeDynamicOpaque();
    }

}
// End of ssv_seal_cipher_tkn4.java
