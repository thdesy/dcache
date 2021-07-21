Third-Party Transfer Examples
==================================

Third-party copies emulate on HTTP a control/data channel-like tarnsfer model as with FTP.

In FTP a client can open a control-channel to a FTP server, that can initiates transfers between a source and destination on a separate data channel. In contrast with HTTP, the data transfer is happening between the client and server only.

With third-party transfers, a Macaroon is requested from the source or destination. The Macaroon is then naded over to the other party, which initiates a read or write HTTP transfer.



## gfal2

gfal-copy --copy-mode push 