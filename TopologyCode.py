from mininet.topo import Topo
from mininet.link import TCLink

class MyTopo(Topo):
    "Topologia z podwojnym laczem miedzy S1 i S2"

    def build(self):
        s1 = self.addSwitch('s1', dpid='1') # dpid wazne dla Javy!
        s2 = self.addSwitch('s2', dpid='2')

        h1 = self.addHost('h1', ip='10.0.0.1')
        h2 = self.addHost('h2', ip='10.0.0.2')
        h3 = self.addHost('h3', ip='10.0.0.3')
        h4 = self.addHost('h4', ip='10.0.0.4')
        h5 = self.addHost('h5', ip='10.0.0.5')
        h6 = self.addHost('h6', ip='10.0.0.6')

        self.addLink(h1, s1)
        self.addLink(h2, s1)
        self.addLink(h3, s1)
        self.addLink(h4, s2)
        self.addLink(h5, s2)
        self.addLink(h6, s2)

        # DWA LINKI (Porty 4 i 5 utworza sie automatycznie)
        self.addLink(s1, s2, port1=4, port2=4)
        self.addLink(s1, s2, port1=5, port2=5)

topos = { 'mytopo': ( lambda: MyTopo() ) }
