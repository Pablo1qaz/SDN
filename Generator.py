#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys
import subprocess
import time
import random

# --- KONFIGURACJA ---

# Lista adresow IP WSZYSTKICH serwerow z topologii (h4, h5, h6)
# Dzieki temu ruch rozlozy sie na cala siec
SERVERS = ['10.0.0.4', '10.0.0.5', '10.0.0.6']

# Przypisanie portow (zgodne z kontrolerem Java)
PORTS = {
    'GAME': 5001,      # Port 4 w Switchu (VIP)
    'VIDEO': 5002,     # Port 4 w Switchu (VIP)
    'DOWNLOAD': 5003   # Port 5 w Switchu (TLO)
}

def print_usage():
    print "Uzycie: python traffic_gen.py [TYP_RUCHU]"
    print "Dostepne typy: GAME, VIDEO, DOWNLOAD"
    print "Przyklad 1 (Losowo): python traffic_gen.py"
    print "Przyklad 2 (Tylko gra): python traffic_gen.py GAME"

def generate_traffic(forced_type=None):
    print "--- START GENERATORA RUCHU ---"
    print "Cele (Serwery): {}".format(SERVERS)
    
    if forced_type:
        print "TRYB WYMUSZONY: Wysylam tylko ruch typu {}".format(forced_type)
    else:
        print "TRYB MIESZANY: Losowe typy ruchu (Gra/Wideo/Pliki)"
        
    print "Nacisnij CTRL+C aby zakonczyc."

    try:
        while True:
            # 1. Wybor losowego serwera z listy (rozlozenie obciazenia)
            target_ip = random.choice(SERVERS)

            # 2. Wybor typu ruchu
            if forced_type:
                traffic_type = forced_type
            else:
                traffic_type = random.choice(['GAME', 'VIDEO', 'DOWNLOAD'])
            
            port = PORTS[traffic_type]
            
            # Czas trwania jednej sesji
            duration = random.randint(5, 8) 
            
            cmd = []
            
            if traffic_type == 'GAME':
                # Symulacja GRY: Male pakiety (len 100), mala przepustowosc (500K), UDP
                bw = "500K"
                print "[>] Cel: {}:{} | Typ: GRA (UDP, {}) przez {}s...".format(target_ip, port, bw, duration)
                cmd = ["iperf", "-c", target_ip, "-u", "-p", str(port), "-b", bw, "-t", str(duration), "-l", "100"] 
            
            elif traffic_type == 'VIDEO':
                # Symulacja WIDEO: Wieksza przepustowosc (2-5M), UDP
                bw = "{}M".format(random.randint(2, 5))
                print "[>] Cel: {}:{} | Typ: STREAM (UDP, {}) przez {}s...".format(target_ip, port, bw, duration)
                cmd = ["iperf", "-c", target_ip, "-u", "-p", str(port), "-b", bw, "-t", str(duration)]
            
            elif traffic_type == 'DOWNLOAD':
                # Symulacja POBIERANIA: Maksymalna przepustowosc TCP
                print "[>] Cel: {}:{} | Typ: POBIERANIE (TCP) przez {}s...".format(target_ip, port, duration)
                cmd = ["iperf", "-c", target_ip, "-p", str(port), "-t", str(duration)]

            # Uruchomienie procesu iperf
            process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            stdout, stderr = process.communicate()
            
            # Wypisanie wyniku
            if stdout:
                lines = stdout.strip().split('\n')
                result_line = lines[-1] if len(lines) > 0 else "Wykonano"
                print "    [Wynik]: {}".format(result_line)
            else:
                err_msg = stderr.strip() if stderr else "Blad polaczenia"
                print "    [Blad]: {}".format(err_msg)
            
            # Losowa przerwa miedzy sesjami (symulacja zachowania czlowieka)
            sleep_time = random.randint(1, 3)
            print "    (Czekam {}s...)\n".format(sleep_time)
            time.sleep(sleep_time)

    except KeyboardInterrupt:
        print "\n--- ZATRZYMANO GENERATOR ---"

if __name__ == "__main__":
    # Obsluga argumentu z linii komend
    mode = None
    if len(sys.argv) > 1:
        arg = sys.argv[1].upper()
        if arg in ['GAME', 'VIDEO', 'DOWNLOAD']:
            mode = arg
        else:
            print_usage()
            sys.exit(1)
            
    generate_traffic(mode)
