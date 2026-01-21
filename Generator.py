#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys
import subprocess
import time
import random
import argparse # Dodano do obsługi argumentów

# --- KONFIGURACJA ---

SERVERS = ['10.0.0.4', '10.0.0.5', '10.0.0.6']

PORTS = {
    'GAME': 5001,      # Port 4 w Switchu (VIP)
    'VIDEO': 5002,     # Port 4 w Switchu (VIP)
    'DOWNLOAD': 5003   # Port 5 w Switchu (TLO)
}

def generate_traffic(forced_type=None, forced_ip=None):
    print "--- START GENERATORA RUCHU ---"
    
    # Informacja o trybie IP
    if forced_ip:
        print "TRYB IP: Wysylam TYLKO do serwera {}".format(forced_ip)
    else:
        print "TRYB IP: Losowe serwery z listy {}".format(SERVERS)

    # Informacja o trybie Typu
    if forced_type:
        print "TRYB TYPU: Wysylam TYLKO ruch typu {}".format(forced_type)
    else:
        print "TRYB TYPU: Mieszany (Losowe typy ruchu)"
        
    print "Nacisnij CTRL+C aby zakonczyc."

    try:
        while True:
            # 1. Wybor serwera (wymuszony lub losowy)
            if forced_ip:
                target_ip = forced_ip
            else:
                target_ip = random.choice(SERVERS)

            # 2. Wybor typu ruchu (wymuszony lub losowy)
            if forced_type:
                traffic_type = forced_type
            else:
                traffic_type = random.choice(['GAME', 'VIDEO', 'DOWNLOAD'])
            
            port = PORTS[traffic_type]
            
            # Czas trwania jednej sesji
            duration = random.randint(5, 8) 
            
            cmd = []
            
            if traffic_type == 'GAME':
                # Zmienilem bw na 500K (zgodnie z logika gry), bo 500M to bardzo duzo jak na UDP game
                bw = "500K" 
                print "[>] Cel: {}:{} | Typ: GRA (UDP, {}) przez {}s...".format(target_ip, port, bw, duration)
                cmd = ["iperf", "-c", target_ip, "-u", "-p", str(port), "-b", bw, "-t", str(duration), "-l", "100"] 
            
            elif traffic_type == 'VIDEO':
                bw = "20M"
                print "[>] Cel: {}:{} | Typ: STREAM (UDP, {}) przez {}s...".format(target_ip, port, bw, duration)
                cmd = ["iperf", "-c", target_ip, "-u", "-p", str(port), "-b", bw, "-t", str(duration)]
            
            elif traffic_type == 'DOWNLOAD':
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
            
            sleep_time = random.randint(2, 3)
            print "    (Czekam {}s...)\n".format(sleep_time)
            time.sleep(sleep_time)

    except KeyboardInterrupt:
        print "\n--- ZATRZYMANO GENERATOR ---"

if __name__ == "__main__":
    # Uzycie argparse dla wygodniejszej obslugi flag
    parser = argparse.ArgumentParser(description='Generator ruchu sieciowego dla Mininet')
    
    parser.add_argument('--type', '-t', 
                        choices=['GAME', 'VIDEO', 'DOWNLOAD'], 
                        help='Wymus konkretny typ ruchu (np. GAME)', 
                        default=None)
                        
    parser.add_argument('--ip', '-i', 
                        help='Wymus konkretny adres IP serwera (np. 10.0.0.4)', 
                        default=None)

    args = parser.parse_args()
    
    # Uruchomienie z sparsowanymi argumentami
    generate_traffic(forced_type=args.type, forced_ip=args.ip)
