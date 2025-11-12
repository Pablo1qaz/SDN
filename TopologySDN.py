#!/usr/bin/env python

from mininet.net import Mininet
from mininet.topo import Topo
from mininet.node import Controller
from mininet.log import info

class DiagramTopo(Topo):
    
    def build(self):

        info('*** Dodawanie przelacznikow\n')
        s1 = self.addSwitch('S1')
        s2 = self.addSwitch('S2')

        info('*** Dodawanie klientow\n')
        h1 = self.addHost('Client1')
        h2 = self.addHost('Client2')
        h3 = self.addHost('Client3')

        info('*** Dodawanie serwerow\n')
        h4 = self.addHost('Server1')
        h5 = self.addHost('Server2')
        h6 = self.addHost('Server3')

        info('*** Tworzenie linkow\n')
        self.addLink(h1, s1)
        self.addLink(h2, s1)
        self.addLink(h3, s1)

        self.addLink(h4, s2)
        self.addLink(h5, s2)
        self.addLink(h6, s2)

        self.addLink(s1, s2)


topos = { 'diagramtopo': ( lambda: DiagramTopo() ) }