package pl.edu.agh.kt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionEnqueue;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

public class SdnLabListener implements IFloodlightModule, IOFMessageListener {

    protected IFloodlightProviderService floodlightProvider;
    protected static Logger logger;

    // Pola do śledzenia aktywności gry
    private long lastGamePacketTime = 0;
    private static final long GAME_ACTIVE_THRESHOLD_MS = 3000;

    @Override
    public String getName() {
        return SdnLabListener.class.getSimpleName();
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
        l.add(IFloodlightProviderService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        logger = LoggerFactory.getLogger(SdnLabListener.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        logger.info("******************* SDN LAB LISTENER START **************************");
    }

    @Override
    public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

        if (msg.getType() != OFType.PACKET_IN) {
            return Command.CONTINUE;
        }

        OFPacketIn pin = (OFPacketIn) msg;
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // 1. OBSLUGA ARP
        if (eth.getEtherType() == EthType.ARP) {
            doPacketOut(sw, pin, OFPort.FLOOD);
            return Command.CONTINUE;
        }

        // 2. OBSLUGA IP
        if (eth.getEtherType() == EthType.IPv4) {
            IPv4 ipv4 = (IPv4) eth.getPayload();
            OFPort outPort = OFPort.FLOOD;
            
            // Zmienna queueId: -1 oznacza brak kolejki (standard output)
            long queueId = -1;

            DatapathId dpid = sw.getId();
            
            // --- LOGIKA DLA SWITCHA S1 ---
            if (dpid.getLong() == 1) {
                // A. Ruch w strone serwerow
                if (isServerIP(ipv4.getDestinationAddress())) {
                    
                    if (ipv4.getProtocol() == IpProtocol.UDP) {
                        UDP udp = (UDP) ipv4.getPayload();
                        int dstPort = udp.getDestinationPort().getPort();

                        if (dstPort == 5001) {
                            // GRA (5001)
                            lastGamePacketTime = System.currentTimeMillis();
                            outPort = OFPort.of(4);
                            queueId = 1; 
                            logger.info("S1: Wykryto GRE (UDP 5001) -> Port 4 (Kolejka 1)");
                        } 
                        else if (dstPort == 5002) {
                            // VIDEO (5002)
                            outPort = OFPort.of(4);
                            if (System.currentTimeMillis() - lastGamePacketTime < GAME_ACTIVE_THRESHOLD_MS) {
                                queueId = 2; // Ograniczona (Gra aktywna)
                                logger.info("S1: VIDEO (UDP 5002) -> Port 4 [Kolejka 2] (Gra AKTYWNA)");
                            } else {
                                queueId = 0; // Domyślna (Gra nieaktywna)
                                logger.info("S1: VIDEO (UDP 5002) -> Port 4 [Kolejka 0] (Gra NIEAKTYWNA)");
                            }
                        } 
                        else {
                            // [ZMIANA] Inny UDP -> Tło (Port 5) + Kolejka 0
                            outPort = OFPort.of(5);
                            // logger.info("S1: Inny UDP -> Port 5 (Kolejka 0)");
                        }
                    } 
                    else if (ipv4.getProtocol() == IpProtocol.TCP) {
                        TCP tcp = (TCP) ipv4.getPayload();
                        int dstPort = tcp.getDestinationPort().getPort();
                        
                        if (dstPort == 5003) {
                            // [ZMIANA] TŁO (TCP 5003) -> Port 5 + Kolejka 0
                            outPort = OFPort.of(5);
                            logger.info("S1: Wykryto TLO (TCP 5003) -> Port 5 (Kolejka 0)");
                        } else {
                            // [ZMIANA] Inny TCP -> Port 5 + Kolejka 0
                            outPort = OFPort.of(5);
                        }
                    } else {
                        // [ZMIANA] Inny protokół IP -> Port 5 + Kolejka 0
                        outPort = OFPort.of(5);
                    }
                } 
                // B. Ruch lokalny
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.1"))) outPort = OFPort.of(1);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.2"))) outPort = OFPort.of(2);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.3"))) outPort = OFPort.of(3);
            }

            // --- LOGIKA DLA SWITCHA S2 ---
            else if (dpid.getLong() == 2) {
                if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.4"))) outPort = OFPort.of(1);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.5"))) outPort = OFPort.of(2);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.6"))) outPort = OFPort.of(3);
                else {
                    outPort = OFPort.of(4);
                }
            }

            // --- INSTALACJA REGUL ---
            if (outPort != OFPort.FLOOD) {
                if (queueId != -1) {
                    // Instalujemy regułę z akcją ENQUEUE
                    installRuleWithQueue(sw, ipv4, outPort, queueId);
                    doPacketOutWithQueue(sw, pin, outPort, queueId);
                } else {
                    // Instalujemy regułę z akcją OUTPUT (Standard)
                    installRule(sw, ipv4, outPort);
                    doPacketOut(sw, pin, outPort);
                }
            } else {
                doPacketOut(sw, pin, OFPort.FLOOD);
            }
            
            return Command.STOP;
        }

        return Command.CONTINUE;
    }

    // --- Metody pomocnicze ---

    private boolean isServerIP(IPv4Address ip) {
        return ip.equals(IPv4Address.of("10.0.0.4")) || 
               ip.equals(IPv4Address.of("10.0.0.5")) || 
               ip.equals(IPv4Address.of("10.0.0.6"));
    }

    // Instaluje regułę z kolejką
    private void installRuleWithQueue(IOFSwitch sw, IPv4 ipv4, OFPort outPort, long queueId) {
        Match.Builder mb = sw.getOFFactory().buildMatch();
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
          .setExact(MatchField.IPV4_DST, ipv4.getDestinationAddress());

        if (ipv4.getProtocol() == IpProtocol.UDP) {
            mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
            UDP udp = (UDP) ipv4.getPayload();
            mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
        } else if (ipv4.getProtocol() == IpProtocol.TCP) { // Dodano obsługę TCP w Match dla kolejek
            mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
            TCP tcp = (TCP) ipv4.getPayload();
            mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
        }

        OFActionEnqueue action = sw.getOFFactory().actions().buildEnqueue()
                .setPort(outPort)
                .setQueueId(queueId)
                .build();

        OFFlowMod flowAdd = sw.getOFFactory().buildFlowAdd()
                .setBufferId(OFBufferId.NO_BUFFER)
                .setHardTimeout(30)
                .setIdleTimeout(2) 
                .setPriority(200)
                .setMatch(mb.build())
                .setActions(Collections.singletonList((OFAction) action))
                .build();

        sw.write(flowAdd);
    }

    // Standardowa reguła Output
    private void installRule(IOFSwitch sw, IPv4 ipv4, OFPort outPort) {
        Match.Builder mb = sw.getOFFactory().buildMatch();
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
          .setExact(MatchField.IPV4_DST, ipv4.getDestinationAddress());

        if (ipv4.getProtocol() == IpProtocol.UDP) {
            mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
            UDP udp = (UDP) ipv4.getPayload();
            mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
        } else if (ipv4.getProtocol() == IpProtocol.TCP) {
            mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
            TCP tcp = (TCP) ipv4.getPayload();
            mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
        }

        OFActionOutput action = sw.getOFFactory().actions().buildOutput()
                .setPort(outPort)
                .setMaxLen(Integer.MAX_VALUE)
                .build();

        OFFlowMod flowAdd = sw.getOFFactory().buildFlowAdd()
                .setBufferId(OFBufferId.NO_BUFFER)
                .setHardTimeout(30)
                .setIdleTimeout(1)
                .setPriority(100)
                .setMatch(mb.build())
                .setActions(Collections.singletonList((OFAction) action))
                .build();

        sw.write(flowAdd);
    }

    // PacketOut z Enqueue
    private void doPacketOutWithQueue(IOFSwitch sw, OFPacketIn pin, OFPort outPort, long queueId) {
        OFActionEnqueue action = sw.getOFFactory().actions().buildEnqueue()
                .setPort(outPort)
                .setQueueId(queueId)
                .build();

        OFPacketOut po = sw.getOFFactory().buildPacketOut()
                .setBufferId(pin.getBufferId())
                .setInPort(pin.getInPort())
                .setActions(Collections.singletonList((OFAction) action))
                .setData(pin.getData())
                .build();

        sw.write(po);
    }

    // Standardowy PacketOut
    private void doPacketOut(IOFSwitch sw, OFPacketIn pin, OFPort outPort) {
        OFActionOutput action = sw.getOFFactory().actions().buildOutput()
                .setPort(outPort)
                .setMaxLen(Integer.MAX_VALUE)
                .build();

        OFPacketOut po = sw.getOFFactory().buildPacketOut()
                .setBufferId(pin.getBufferId())
                .setInPort(pin.getInPort())
                .setActions(Collections.singletonList((OFAction) action))
                .setData(pin.getData())
                .build();

        sw.write(po);
    }
}
