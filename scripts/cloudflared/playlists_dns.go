package main

import (
	"context"
	"net"
	"net/netip"
	"os"
	"strconv"
	"strings"
	"sync/atomic"
	"time"
)

// Android's libc handles host lookups, but Go's SRV/TXT resolver still reads
// /etc/resolv.conf, which Android does not provide. The app supplies the active
// network's DNS servers so edge discovery never falls back to localhost:53.
func playlistsDNSResolver(addresses string) *net.Resolver {
	var servers []string
	for _, address := range strings.Split(addresses, ",") {
		host, port, err := net.SplitHostPort(strings.TrimSpace(address))
		_, ipErr := netip.ParseAddr(host)
		portNumber, portErr := strconv.Atoi(port)
		if err == nil && ipErr == nil && portErr == nil && portNumber > 0 && portNumber <= 65535 {
			servers = append(servers, net.JoinHostPort(host, port))
		}
	}
	if len(servers) == 0 {
		return nil
	}
	var next atomic.Uint64
	return &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			server := servers[(next.Add(1)-1)%uint64(len(servers))]
			dialer := net.Dialer{Timeout: 4 * time.Second}
			return dialer.DialContext(ctx, network, server)
		},
	}
}

func init() {
	if resolver := playlistsDNSResolver(os.Getenv("PLAYLISTS_DNS_SERVERS")); resolver != nil {
		net.DefaultResolver = resolver
	}
}
