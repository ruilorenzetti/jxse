/*
 * Copyright (c) 2001-2007 Sun Microsystems, Inc.  All rights reserved.
 *
 *  The Sun Project JXTA(TM) Software License
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, 
 *     this list of conditions and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution.
 *
 *  3. The end-user documentation included with the redistribution, if any, must 
 *     include the following acknowledgment: "This product includes software 
 *     developed by Sun Microsystems, Inc. for JXTA(TM) technology." 
 *     Alternately, this acknowledgment may appear in the software itself, if 
 *     and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must 
 *     not be used to endorse or promote products derived from this software 
 *     without prior written permission. For written permission, please contact 
 *     Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA", nor may 
 *     "JXTA" appear in their name, without prior written permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 *  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND 
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL SUN 
 *  MICROSYSTEMS OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  JXTA is a registered trademark of Sun Microsystems, Inc. in the United 
 *  States and other countries.
 *
 *  Please see the license information page at :
 *  <http://www.jxta.org/project/www/license.html> for instructions on use of 
 *  the license in source files.
 *
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many individuals 
 *  on behalf of Project JXTA. For more information on Project JXTA, please see 
 *  http://www.jxta.org.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation. 
 */
package net.jxta.impl.rendezvous;

import net.jxta.endpoint.EndpointAddress;
import net.jxta.endpoint.EndpointListener;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.Messenger;
import net.jxta.endpoint.TextDocumentMessageElement;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.impl.endpoint.EndpointUtils;
import net.jxta.impl.endpoint.TransportUtils;
import net.jxta.impl.rendezvous.server.RendezvouseServiceServer;
import net.jxta.impl.rendezvous.rendezvousMeter.RendezvousMeterBuildSettings;
import net.jxta.impl.rendezvous.rpv.PeerViewElement;
import net.jxta.logging.Logger;
import net.jxta.logging.Logging;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.RouteAdvertisement;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * Base class for providers which implement the JXTA Standard Rendezvous
 * Protocol.
 *
 * @see <a href="https://jxta-spec.dev.java.net/nonav/JXTAProtocols.html#proto-rvp" target="_blank">JXTA Protocols Specification : Rendezvous Protocol</a>
 */
public abstract class RendezVousService extends RendezVousServiceProvider {
    private final static Logger LOG = Logging.getLogger(RendezVousService.class.getName());

    public final static String ConnectRequest = "ConnectRequest";
    public final static String DisconnectRequest = "DisconnectRequest";
    public final static String ConnectedPeerReply = "ConnectedPeerReply";
    public final static String ConnectedLeaseReply = "ConnectedLeaseReply";
    public final static String ConnectedRendezvousAdvertisementReply = "RendezvousAdvertisementReply";
    
    public final static String ConnectRequestNotification = "ConnectRequestNotification";
    public final static String DisconnectRequestNotification = "DisonnectRequestNotification";
    

    /**
     * Default Maximum TTL.
     */
    protected static final int DEFAULT_MAX_TTL = 200;

    protected final String pName;
    protected final String pParam;

    /**
     * The registered handler for messages using the Standard Rendezvous
     * Protocol.
     *
     * @see <a href="https://jxta-spec.dev.java.net/nonav/JXTAProtocols.html#proto-rvp" target="_blank">JXTA Protocols Specification : Rendezvous Protocol
     */
    private RendezvousMessageListener handler;

    /**
     * Interface for listeners to : &lt;assignedID>/<group-unique>
     */
    protected interface RendezvousMessageListener extends EndpointListener {}

    /**
     * Constructor
     *
     * @param group      the PeerGroup
     * @param rdvService the parent rendezvous service
     */
    protected RendezVousService(PeerGroup group, RendezVousServiceImpl rdvService) {
        super(group, rdvService);

        MAX_TTL = DEFAULT_MAX_TTL;

        pName = rdvService.getAssignedID().toString();
        pParam = group.getPeerGroupID().getUniqueValue().toString();
    }

    /**
     * Start the rendezvous service with a listener.
     * 
     * @param argv module start arguments
     * @param handler rdv protocol handler instance
     * @return module start status code
     */
    protected int startApp(String[] argv, RendezvousMessageListener handler) {
        this.handler = handler;

        rendezvousServiceImplementation.endpoint.addIncomingMessageListener(handler, pName, null);

        return super.startApp(argv);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopApp() {
        EndpointListener shouldbehandler = rendezvousServiceImplementation.endpoint.removeIncomingMessageListener(pName, null);

        if (handler != shouldbehandler) 
            Logging.logCheckedWarning(LOG, "Unregistered listener was not as expected.", handler, " != ", shouldbehandler);

        super.stopApp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processReceivedMessage(Message message, RendezVousPropagateMessage propHdr, EndpointAddress sourceAddress, EndpointAddress destinationAddress) {
        if (sourceAddress.getProtocolName().equalsIgnoreCase("jxta")) {
            String idstr = ID.URIEncodingName + ":" + ID.URNNamespace + ":" + sourceAddress.getProtocolAddress();

            ID peerId;

            try {
                peerId = IDFactory.fromURI(new URI(idstr));
            } catch (URISyntaxException badID) {
                Logging.logCheckedWarning(LOG, "Bad ID in message\n", badID);
                return;
            }

            if (!peerGroup.getPeerID().equals(peerId)) {
                PeerConnection peerConnection = getPeerConnection(peerId);

                if (null == peerConnection) {
                    PeerViewElement peerViewElement;

                    if (this instanceof RendezvouseServiceServer) {                        
                        peerViewElement = ((RendezvouseServiceServer) this).rendezvousPeersView.getPeerViewElement(peerId);
                    } else {
                        peerViewElement = null;
                    }

                    if (null == peerViewElement) {
                        Logging.logCheckedDebug(LOG, "Received ", message, " (", propHdr.getMsgId(), ") from unrecognized peer : ", peerId);

                        propHdr.setTTL(Math.min(propHdr.getTTL(), 3)); // will be reduced during repropagate stage.

                        // FIXME 20040503 bondolo need to add tombstones so that we don't end up spamming disconnects.
                        if (rendezvousServiceImplementation.isRendezVous() || (getPeerConnections().length > 0)) {
                            //Edge peers with no rdv should not send disconnect.
                            sendDisconnect(peerId, null);
                        }
                    } else {
                        Logging.logCheckedDebug(LOG, "Received ", message, " (", propHdr.getMsgId(), ") from ", peerViewElement);
                    }
                } else {
                    Logging.logCheckedDebug(LOG, "Received ", message, " (", propHdr.getMsgId(), ") from ", peerConnection);
                }
            } else {
                Logging.logCheckedDebug(LOG, "Received ", message, " (", propHdr.getMsgId(), ") from loopback.");
            }
        } else {
            Logging.logCheckedDebug(LOG, "Received ", message, " (", propHdr.getMsgId(), ") from network -- repropagating with TTL 2");
            propHdr.setTTL(Math.min(propHdr.getTTL(), 3)); // will be reduced during repropagate stage.
        }
        super.processReceivedMessage(message, propHdr, sourceAddress, destinationAddress);
    }

    /**
     * {@inheritDoc}
     * @param destPeerIDs
     */
    @Override
    public void propagate(Enumeration<? extends ID> destPeerIDs, Message msg, String serviceName, String serviceParam, int initialTTL) {
        msg = msg.clone();
        int useTTL = Math.min(initialTTL, MAX_TTL);

        Logging.logCheckedDebug(LOG, "Propagating ", msg, "(TTL=", useTTL, ") to :\n\tsvc name:", serviceName, "\tsvc params:", serviceParam);

        RendezVousPropagateMessage propHdr = updatePropHeader(msg, getPropHeader(msg), serviceName, serviceParam, useTTL);

        if (null != propHdr) {
            int numPeers = 0;

            try {
                while (destPeerIDs.hasMoreElements()) {
                    ID dest = destPeerIDs.nextElement();

                    try {
                        PeerConnection peerConnection = getPeerConnection(dest);

                        // TODO: make use of PeerView connections as well
                        if (null == peerConnection) {
                            Logging.logCheckedDebug(LOG, "Sending ", msg, " (", propHdr.getMsgId(), ") to ", dest);

                            EndpointAddress addr = makeAddress(dest, PropSName, PropPName);
                            Messenger messenger = rendezvousServiceImplementation.endpoint.getMessengerImmediate(addr, null);

                            if (null != messenger) {
                                try {
                                    messenger.sendMessage(msg);
                                } catch (IOException ignored) {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        } else {
                            Logging.logCheckedDebug(LOG, "Sending ", msg, " (", propHdr.getMsgId(), ") to ", peerConnection);

                            if (peerConnection.isConnected()) {
                                peerConnection.sendMessage(msg.clone(), PropSName, PropPName);
                            } else {
                                continue;
                            }
                        }

                        numPeers++;
                    } catch (Exception exception) {
                        Logging.logCheckedWarning(LOG, "Failed to send ", msg, " (", propHdr.getMsgId(), ") to ", dest);
                    }
                }
            } finally {
                if (RendezvousMeterBuildSettings.RENDEZVOUS_METERING && (rendezvousMeter != null)) {
                    rendezvousMeter.propagateToPeers(numPeers);
                }
                Logging.logCheckedDebug(LOG, "Propagated ", msg, " (", propHdr.getMsgId(), ") to ", numPeers, " peers.");
            }
        } else {
            Logging.logCheckedDebug(LOG, "Declined to send ", msg, " ( no propHdr )");
        }
    }

    /**
     * {@inheritDoc}
     * @throws java.io.IOException
     */
    @Override
    public void propagateToNeighbors(Message msg, String serviceName, String serviceParam, int initialTTL) throws IOException {

        msg = msg.clone();
        int useTTL = Math.min(initialTTL, MAX_TTL);

        Logging.logCheckedDebug(LOG, "Propagating ", msg, "(TTL=", useTTL, ") to neighbors to :\n\tsvc name:", serviceName, "\tsvc params:", serviceParam);

        RendezVousPropagateMessage propHdr = updatePropHeader(msg, getPropHeader(msg), serviceName, serviceParam, useTTL);

        if (null != propHdr) {
            try {
                sendToNetwork(msg, propHdr);

                if (RendezvousMeterBuildSettings.RENDEZVOUS_METERING && (rendezvousMeter != null)) {
                    rendezvousMeter.propagateToNeighbors();
                }
            } catch (IOException failed) {
                if (RendezvousMeterBuildSettings.RENDEZVOUS_METERING && (rendezvousMeter != null)) {
                    rendezvousMeter.propagateToNeighborsFailed();
                }

                throw failed;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void repropagate(Message msg, RendezVousPropagateMessage propHdr, String serviceName, String serviceParam) {

        msg = msg.clone();

        Logging.logCheckedDebug(LOG, "Repropagating ", msg, " (", propHdr.getMsgId(), ")");

        if (RendezvousMeterBuildSettings.RENDEZVOUS_METERING && (rendezvousMeter != null)) {
            rendezvousMeter.receivedMessageRepropagatedInGroup();
        }

        try {
            propHdr = updatePropHeader(msg, propHdr, serviceName, serviceParam, MAX_TTL);

            if (null != propHdr) {
                // Note (hamada): This is an unnecessary operation, and serves
                // no purpose other than the additional loads it imposes on the
                // rendezvous.  Local subnet network operations should be (and are)
                // sufficient to achieve the goal.
                // sendToEachConnection(msg, propHdr);
                sendToNetwork(msg, propHdr);

            } else {
                Logging.logCheckedDebug(LOG, "No propagate header, declining to repropagate ", msg, ")");
            }
        } catch (Exception ez1) {
            // Not much we can do
            if (propHdr != null) {
                Logging.logCheckedWarning(LOG, "Failed to repropagate ", msg, " (", propHdr.getMsgId(), ")\n", ez1);
            } else {
                Logging.logCheckedWarning(LOG, "Could to repropagate ", msg, "\n", ez1);
            }
        }
    }

    /**
     * Returns the peer connection or null if not present.
     *
     * @param id the node ID
     * @return PeerConnection the peer connection or null if not present.
     */
    public abstract PeerConnection getPeerConnection(ID id);

    /**
     * Returns an array of the current peer connections.
     *
     * @return An array of the current peer connections.
     */
    protected abstract PeerConnection[] getPeerConnections();

    /**
     * Sends to all connected peers.
     * <p/>
     * Note: The original msg is not modified and may be reused upon return.
     *
     * @param msg     The message to be sent.
     * @param propHdr The propagation header associated with the message.
     * @return the number of nodes the message was sent to
     */
    protected int sendToEachConnection(Message msg, RendezVousPropagateMessage propHdr) {
        List<PeerConnection> peersConnections = Arrays.asList(getPeerConnections());
        int sentToPeers = 0;

        Logging.logCheckedDebug(LOG, "Sending ", msg, "(", propHdr.getMsgId(), ") to ", peersConnections.size(), " peers.");

        for (PeerConnection peerConnection : peersConnections) {
            // Check if this rendezvous has already processed this propagated message.
            if (!peerConnection.isConnected()) {
                Logging.logCheckedDebug(LOG, "Skipping ", peerConnection, " for ", msg, "(", propHdr.getMsgId(), ") -- disconnected.");                
                continue;
            }

            if (propHdr.isVisited(peerConnection.getPeerID().toURI())) {
                Logging.logCheckedDebug(LOG, "Skipping ", peerConnection, " for ", msg, "(", propHdr.getMsgId(), ") -- already visited.");                
                continue;
            }

            Logging.logCheckedDebug(LOG, "Sending ", msg, "(", propHdr.getMsgId(), ") to ", peerConnection);

            boolean sent;
            if (TransportUtils.isAnSRDIMessage(msg)) {
                sent = peerConnection.sendMessageB(msg.clone(), PropSName, PropPName);
            } else {
                sent = peerConnection.sendMessage(msg.clone(), PropSName, PropPName);
            }
            
            if (sent) {
                sentToPeers++;
            }
        }

        Logging.logCheckedDebug(LOG, "Sent ", msg, "(", propHdr.getMsgId(), ") to ", sentToPeers, " of ", peersConnections.size(), " peers.");
        return sentToPeers;
    }

    /**
     * Sends a disconnect message to the specified peer.
     *
     * @param peerId The peer to be disconnected.
     * @param peerAdvertisement   The peer to be disconnected.
     */
    protected void sendDisconnect(ID peerId, PeerAdvertisement peerAdvertisement) {
        Message message = new Message();

        // The request simply includes the local peer advertisement.
        try {
            message.replaceMessageElement(RendezVousServiceProvider.RENDEZVOUS_MESSAGE_NAMESPACE_NAME, new TextDocumentMessageElement(DisconnectRequest, getPeerAdvertisementDoc(), null));
            EndpointAddress addr = makeAddress(peerId, null, null);
            RouteAdvertisement routeAdvertisement = null;

            if (null != peerAdvertisement) {
                routeAdvertisement = EndpointUtils.extractRouteAdv(peerAdvertisement);
            }

            Messenger messenger = rendezvousServiceImplementation.endpoint.getMessengerImmediate(addr, routeAdvertisement);

            if (null == messenger) {
                Logging.logCheckedWarning(LOG, "Could not get messenger for ", peerId);
                return;
            }

            messenger.sendMessage(message, pName, pParam);
        } catch (Exception e) {
            Logging.logCheckedWarning(LOG, "sendDisconnect failed\n", e);
        }
    }

    /**
     * Sends a disconnect message to the specified peer.
     *
     * @param peerConnection The peer to be disconnected.
     */
    protected void sendDisconnect(PeerConnection peerConnection) {

        Message message = new Message();

        // The request simply includes the local peer advertisement.
        try {
            message.replaceMessageElement(RendezVousServiceProvider.RENDEZVOUS_MESSAGE_NAMESPACE_NAME, new TextDocumentMessageElement(DisconnectRequest, getPeerAdvertisementDoc(), null));
            peerConnection.sendMessage(message, pName, pParam);
        } catch (Exception e) {
            Logging.logCheckedWarning(LOG, "sendDisconnect failed\n", e);
        }
    }
}