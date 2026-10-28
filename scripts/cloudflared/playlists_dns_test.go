package main

import (
	"context"
	"encoding/binary"
	"net"
	"testing"
	"time"
)

func TestInvalidDNSAddressesDoNotOverrideResolver(t *testing.T) {
	for _, value := range []string{"", "dns.example:53", "1.1.1.1:0", "garbage"} {
		if playlistsDNSResolver(value) != nil {
			t.Fatalf("accepted invalid DNS servers: %q", value)
		}
	}
}

func TestResolverUsesNetworkDNSForSRVAndTXT(t *testing.T) {
	server, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()
	go func() {
		buffer := make([]byte, 4096)
		for {
			n, peer, err := server.ReadFrom(buffer)
			if err != nil {
				return
			}
			query := buffer[:n]
			if n < 17 {
				continue
			}
			end := 12
			for end < n && query[end] != 0 {
				end += int(query[end]) + 1
			}
			if end+5 > n {
				continue
			}
			end += 5 // root label + query type and class
			kind := binary.BigEndian.Uint16(query[end-4 : end-2])
			var data []byte
			switch kind {
			case 16:
				data = []byte{2, 'o', 'k'} // TXT
			case 33:
				data = []byte{0, 0, 0, 1, 0x1e, 0xa4, 4, 'e', 'd', 'g', 'e', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 0}
			default:
				continue
			}
			answer := append([]byte(nil), query[:end]...)
			answer[2], answer[3] = 0x81, 0x80
			binary.BigEndian.PutUint16(answer[6:8], 1)
			binary.BigEndian.PutUint16(answer[8:10], 0)
			binary.BigEndian.PutUint16(answer[10:12], 0)
			answer = append(answer, 0xc0, 0x0c, byte(kind>>8), byte(kind), 0, 1, 0, 0, 0, 30, 0, byte(len(data)))
			answer = append(answer, data...)
			server.WriteTo(answer, peer)
		}
	}()
	resolver := playlistsDNSResolver(server.LocalAddr().String())
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	txt, err := resolver.LookupTXT(ctx, "features.example")
	if err != nil || len(txt) != 1 || txt[0] != "ok" {
		t.Fatalf("TXT lookup failed: %v %v", txt, err)
	}
	_, srv, err := resolver.LookupSRV(ctx, "v2-origintunneld", "tcp", "example")
	if err != nil || len(srv) != 1 || srv[0].Target != "edge.example." || srv[0].Port != 7844 {
		t.Fatalf("SRV lookup failed: %v %v", srv, err)
	}
}

func TestResolverAcceptsScopedIPv6(t *testing.T) {
	if playlistsDNSResolver("[fe80::1%wlan0]:53") == nil {
		t.Fatal("scoped IPv6 DNS server was rejected")
	}
}
