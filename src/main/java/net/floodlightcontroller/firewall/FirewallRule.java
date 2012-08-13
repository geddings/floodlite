package net.floodlightcontroller.firewall;

import java.util.Arrays;

public class FirewallRule implements Comparable {
	public int ruleid;
	public short src_inport;
	public long src_mac;
	public int src_ip_prefix;
	public int src_ip_bits;
	public short proto_type;
	public short proto_srcport;
	public short proto_dstport;
	public long dst_mac;
	public int dst_ip_prefix;
	public int dst_ip_bits;
	public long switchid;
	public boolean wildcard_src_inport;
	public boolean wildcard_src_mac;
	public boolean wildcard_src_ip;
	public boolean wildcard_proto_type;
	public boolean wildcard_dst_mac;
	public boolean wildcard_dst_ip;
	public boolean wildcard_switchid;
	public int priority = 0;
	public boolean is_denyrule;

	public FirewallRule() {
		this.src_inport = 1;
		this.src_mac = 0;
		this.src_ip_prefix = 0;
		this.src_ip_bits = 32;
		this.proto_type = 0;
		this.proto_srcport = 0;
		this.proto_dstport = 0;
		this.dst_mac = 0;
		this.dst_ip_prefix = 0;
		this.dst_ip_bits = 32;
		this.switchid = -1;
		this.wildcard_src_inport = true;
		this.wildcard_src_mac = true;
		this.wildcard_src_ip = true;
		this.wildcard_proto_type = true;
		this.wildcard_dst_mac = true;
		this.wildcard_dst_ip = true;
		this.wildcard_switchid = true;
		this.priority = 32767;
		this.is_denyrule = false;
		this.ruleid = this.genID();
	}
	
	public int genID() {
		int uid = this.hashCode();
		if (uid <= 0) {
			uid = Math.abs(uid);
			uid *= 15551;
		}
		return uid;
	}
	
	public int compareTo(Object rule) {
        return this.priority - ((FirewallRule)rule).priority;
    }
	
	public boolean isSameAs(FirewallRule r) {
		if (
				this.is_denyrule != r.is_denyrule ||
				this.wildcard_switchid != r.wildcard_switchid ||
				this.wildcard_src_inport != r.wildcard_src_inport ||
				this.wildcard_src_ip != r.wildcard_src_ip ||
				this.wildcard_src_mac != r.wildcard_src_ip ||
				this.wildcard_proto_type != r.wildcard_proto_type ||
				this.wildcard_dst_ip != r.wildcard_dst_ip ||
				this.wildcard_dst_mac != r.wildcard_dst_mac ||
				(this.wildcard_switchid == false && this.switchid != r.switchid) ||
				(this.wildcard_src_inport == false && this.src_inport != r.src_inport) ||
				(this.wildcard_src_ip == false && (this.src_ip_prefix != r.src_ip_prefix || this.src_ip_bits != r.src_ip_bits)) ||
				(this.wildcard_src_mac == false && this.src_mac != r.src_mac) ||
				(this.wildcard_proto_type == false && this.proto_type != r.proto_type) ||
				(this.wildcard_dst_ip == false && (this.dst_ip_prefix != r.dst_ip_prefix || this.dst_ip_bits != r.dst_ip_bits)) ||
				(this.wildcard_dst_mac == false && this.dst_mac != r.dst_mac)
		) {
			return false;
		}
		return true;
	}
	
	@Override
    public int hashCode() {
        final int prime = 2521;
        int result = super.hashCode();
        result = prime * result + src_inport;
        result = prime * result + (int)src_mac;
        result = prime * result + src_ip_prefix;
        result = prime * result + src_ip_bits;
        result = prime * result + proto_type;
        result = prime * result + proto_srcport;
        result = prime * result + proto_dstport;
        result = prime * result + (int)dst_mac;
        result = prime * result + dst_ip_prefix;
        result = prime * result + dst_ip_bits;
        result = prime * result + (int)switchid;
        result = prime * result + priority;
        result = prime * result + (new Boolean(is_denyrule)).hashCode();
        result = prime * result + (new Boolean(wildcard_switchid)).hashCode();
        result = prime * result + (new Boolean(wildcard_src_inport)).hashCode();
        result = prime * result + (new Boolean(wildcard_src_ip)).hashCode();
        result = prime * result + (new Boolean(wildcard_src_mac)).hashCode();
        result = prime * result + (new Boolean(wildcard_proto_type)).hashCode();
        result = prime * result + (new Boolean(wildcard_dst_ip)).hashCode();
        result = prime * result + (new Boolean(wildcard_dst_mac)).hashCode();
        return result;
    }
}
