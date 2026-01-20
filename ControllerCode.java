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
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
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
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

public class SdnLabListener implements IFloodlightModule, IOFMessageListener {

    protected IFloodlightProviderService floodlightProvider;
    protected static Logger logger;

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

        // Obslugujemy tylko Packet_In
        if (msg.getType() != OFType.PACKET_IN) {
            return Command.CONTINUE;
        }

        OFPacketIn pin = (OFPacketIn) msg;
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // 1. OBSLUGA ARP (Niezbedne do Pingu!)
        if (eth.getEtherType() == EthType.ARP) {
            // Jeśli to ARP, wyslij jako FLOOD (do wszystkich portow)
            doPacketOut(sw, pin, OFPort.FLOOD);
            return Command.CONTINUE;
        }

        // 2. OBSLUGA IP (Logika Sterowania)
        if (eth.getEtherType() == EthType.IPv4) {
            IPv4 ipv4 = (IPv4) eth.getPayload();
            OFPort outPort = OFPort.FLOOD; // Domyslnie flood, jesli nie znajdziemy reguly

            DatapathId dpid = sw.getId();
            
            // --- LOGIKA DLA SWITCHA S1 (ID ...:01) ---
            if (dpid.getLong() == 1) {
                // A. Ruch w strone serwerow (h4, h5, h6) - TU ROBIMY TRAFFIC ENGINEERING
                if (isServerIP(ipv4.getDestinationAddress())) {
                    
                    if (ipv4.getProtocol() == IpProtocol.UDP) {
                        UDP udp = (UDP) ipv4.getPayload();
                        int dstPort = udp.getDestinationPort().getPort();

                        if (dstPort == 5001 || dstPort == 5002) {
                            // GRA lub WIDEO -> Szybki link (Port 4)
                            outPort = OFPort.of(4);
                            logger.info("S1: Wykryto PRIORYTET (UDP " + dstPort + ") -> Port 4");
                        } else {
                            // Inny UDP -> Tło (Port 5)
                            outPort = OFPort.of(5);
                        }
                    } 
                    else if (ipv4.getProtocol() == IpProtocol.TCP) {
                        TCP tcp = (TCP) ipv4.getPayload();
                        int dstPort = tcp.getDestinationPort().getPort();
                        
                        if (dstPort == 5003) {
                            // PLIKI -> Tło (Port 5)
                            outPort = OFPort.of(5);
                            logger.info("S1: Wykryto TLO (TCP 5003) -> Port 5");
                        } else {
                            outPort = OFPort.of(5);
                        }
                    } else {
                        // ICMP (Ping) i inne -> Tło
                        outPort = OFPort.of(5);
                    }
                } 
                // B. Ruch lokalny do klientow
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.1"))) outPort = OFPort.of(1);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.2"))) outPort = OFPort.of(2);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.3"))) outPort = OFPort.of(3);
            }

            // --- LOGIKA DLA SWITCHA S2 (ID ...:02) ---
            else if (dpid.getLong() == 2) {
                // A. Ruch do serwerow (lokalny)
                if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.4"))) outPort = OFPort.of(1);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.5"))) outPort = OFPort.of(2);
                else if (ipv4.getDestinationAddress().equals(IPv4Address.of("10.0.0.6"))) outPort = OFPort.of(3);
                
                // B. Ruch powrotny do S1 (do klientow)
                else {
                    // Odsylamy szybkim kablem (Port 4) zeby nie opozniac powrotow
                    outPort = OFPort.of(4);
                }
            }

            // Jesli znalezlismy konkretny port (nie FLOOD), instalujemy regule na switchu
            if (outPort != OFPort.FLOOD) {
                installRule(sw, ipv4, outPort); // Wgrywa flow do switcha (zapamietuje)
                doPacketOut(sw, pin, outPort);  // Wysyla ten konkretny pakiet
            } else {
                doPacketOut(sw, pin, OFPort.FLOOD); // Nie wiemy gdzie, wiec flood
            }
            
            return Command.STOP; // Przejelismy pakiet, nie przekazuj dalej
        }

        return Command.CONTINUE;
    }

    // --- Metody pomocnicze (zamiast zewnetrznej klasy Flows) ---

    private boolean isServerIP(IPv4Address ip) {
        return ip.equals(IPv4Address.of("10.0.0.4")) || 
               ip.equals(IPv4Address.of("10.0.0.5")) || 
               ip.equals(IPv4Address.of("10.0.0.6"));
    }

    // Instaluje regule (FlowMod) w switchu
    private void installRule(IOFSwitch sw, IPv4 ipv4, OFPort outPort) {
        Match.Builder mb = sw.getOFFactory().buildMatch();
        mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
          .setExact(MatchField.IPV4_DST, ipv4.getDestinationAddress());

        // Jesli to TCP/UDP, dodaj dopasowanie po porcie, zeby nie zablokowac innego ruchu
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
                .setHardTimeout(30) // Regula wygasa po 30s bezczynnosci (dla bezpieczenstwa)
                .setIdleTimeout(10)
                .setPriority(100)
                .setMatch(mb.build())
                .setActions(Collections.singletonList((OFAction) action))
                .build();

        sw.write(flowAdd);
    }

    // Wysyla pojedynczy pakiet (PacketOut)
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
