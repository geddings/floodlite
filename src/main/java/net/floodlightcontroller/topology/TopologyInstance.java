package net.floodlightcontroller.topology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.util.ClusterDFS;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.util.LRUHashMap;

public class TopologyInstance {

    public static final short LT_SH_LINK = 1;
    public static final short LT_BD_LINK = 2;
    public static final short LT_TUNNEL  = 3; 

    public static final int MAX_LINK_WEIGHT = 10000;
    public static final int MAX_PATH_WEIGHT = Integer.MAX_VALUE - MAX_LINK_WEIGHT - 1;
    public static final int PATH_CACHE_SIZE = 1000;

    protected static Logger log = LoggerFactory.getLogger(TopologyInstance.class);

    protected Map<Long, Set<Short>> switchPorts; // Set of ports for each switch
    protected Map<NodePortTuple, Set<Link>> switchPortLinks; // Set of links organized by node port tuple

    protected Set<Long> switches;
    protected Set<NodePortTuple> broadcastDomainPorts;
    protected Set<NodePortTuple> tunnelPorts;
    //protected Set<NodePortTuple> blockedPorts;

    protected Set<Cluster> clusters;  // set of clusters
    protected Map<Long, Cluster> switchClusterMap; // switch to cluster map

    // States for routing
    protected Map<Long, BroadcastTree> destinationRootedTrees;
    protected Map<Long, Set<NodePortTuple>> clusterBroadcastNodePorts;
    protected Map<Long, BroadcastTree> clusterBroadcastTrees;
    protected LRUHashMap<RouteId, Route> pathcache;

    public TopologyInstance() {
        this.switches = new HashSet<Long>();
        this.switchPorts = new HashMap<Long, Set<Short>>();
        this.switchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
        this.broadcastDomainPorts = new HashSet<NodePortTuple>();
        this.tunnelPorts = new HashSet<NodePortTuple>();
    }

    public TopologyInstance(Map<Long, Set<Short>> switchPorts,
                            Map<NodePortTuple, Set<Link>> switchPortLinks,
                            Map<NodePortTuple, Set<Link>> portBroadcastDomainLinks, 
                            Map<NodePortTuple, Set<Link>> tunnelLinks){

        // copy these structures
        this.switches = new HashSet<Long>(switchPorts.keySet());
        this.switchPorts = new HashMap<Long, Set<Short>>(switchPorts);
        this.switchPortLinks = new HashMap<NodePortTuple, Set<Link>>(switchPortLinks);
        this.broadcastDomainPorts = new HashSet<NodePortTuple>(portBroadcastDomainLinks.keySet());
        this.tunnelPorts = new HashSet<NodePortTuple>(tunnelLinks.keySet());

        // create new empty ones.
        //blockedPorts = new HashSet<NodePortTuple>();

        clusters = new HashSet<Cluster>();
        switchClusterMap = new HashMap<Long, Cluster>();
        destinationRootedTrees = new HashMap<Long, BroadcastTree>();
        clusterBroadcastTrees = new HashMap<Long, BroadcastTree>();
        clusterBroadcastNodePorts = new HashMap<Long, Set<NodePortTuple>>();
        pathcache = new LRUHashMap<RouteId, Route>(PATH_CACHE_SIZE);
    }

    public void compute() {
        // Step 1: Compute clusters ignoring broadcast domain links
        // Create nodes for clusters in the higher level topology
        identifyClusters();

        // Step 1.1: Add links to clusters
        addLinksToClusters();

        // Step 2. Compute shortest path trees in each cluster for 
        // unicast routing.  The trees are rooted at the destination.
        // Cost for tunnel links and direct links are the same.
        calculateShortestPathTreeInClusters();

        // Step 3. Compute broadcast tree in each cluster.
        // Cost for tunnel links are high to discourage use of 
        // tunnel links.  The cost is set to the number of nodes
        // in the cluster + 1, to use as minimum number of 
        // clusters as possible.
        calculateBroadcastNodePortsInClusters();

        // Step 4. print topology.
        // printTopology();
    }

    public void printTopology() {
        log.info("-----------------------------------------------");
        log.info("Links: {}",this.switchPortLinks);
        log.info("broadcastDomainPorts: {}", broadcastDomainPorts);
        log.info("tunnelPorts: {}", tunnelPorts);
        log.info("clusters: {}", clusters);
        log.info("destinationRootedTrees: {}", destinationRootedTrees);
        log.info("clusterBroadcastNodePorts: {}", clusterBroadcastNodePorts);
        log.info("-----------------------------------------------");
    }

    protected void addLinksToClusters() {
        for(long s: switches) {
            if (switchPorts.get(s) == null) continue;
            for (short p: switchPorts.get(s)) {
                NodePortTuple np = new NodePortTuple(s, p);
                if (switchPortLinks.get(np) == null) continue;
                if (isBroadcastDomainPort(np)) continue;
                for(Link l: switchPortLinks.get(np)) {
                    if (isBroadcastDomainLink(l)) continue;
                    Cluster c1 = switchClusterMap.get(l.getSrc());
                    Cluster c2 = switchClusterMap.get(l.getDst());
                    if (c1 ==c2) {
                        c1.addLink(l);
                    }
                }
            }
        }
    }

    /**
     * @author Srinivasan Ramasubramanian
     *
     * This function divides the network into clusters. Every cluster is
     * a strongly connected component. The network may contain unidirectional
     * links.  The function calls dfsTraverse for performing depth first
     * search and cluster formation.
     *
     * The computation of strongly connected components is based on
     * Tarjan's algorithm.  For more details, please see the Wikipedia
     * link below.
     *
     * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     */
    public void identifyClusters() {
        Map<Long, ClusterDFS> dfsList = new HashMap<Long, ClusterDFS>();

        if (switches == null) return;

        for (Long key: switches) {
            ClusterDFS cdfs = new ClusterDFS();
            dfsList.put(key, cdfs);
        }
        Set<Long> currSet = new HashSet<Long>();

        for (Long sw: switches) {
            ClusterDFS cdfs = dfsList.get(sw);
            if (cdfs == null) {
                log.error("Do DFS object for switch {} found.", sw);
            }else if (!cdfs.isVisited()) {
                dfsTraverse(0, 1, sw, switches, dfsList, currSet, clusters);
            }
        }
    }


    /**
     * @author Srinivasan Ramasubramanian
     *
     * This algorithm computes the depth first search (DFS) travesral of the
     * switches in the network, computes the lowpoint, and creates clusters
     * (of strongly connected components).
     *
     * The computation of strongly connected components is based on
     * Tarjan's algorithm.  For more details, please see the Wikipedia
     * link below.
     *
     * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
     *
     * The initialization of lowpoint and the check condition for when a
     * cluster should be formed is modified as we do not remove switches that
     * are already part of a cluster.
     *
     * A return value of -1 indicates that dfsTraverse failed somewhere in the middle
     * of computation.  This could happen when a switch is removed during the cluster
     * computation procedure.
     *
     * @param parentIndex: DFS index of the parent node
     * @param currIndex: DFS index to be assigned to a newly visited node
     * @param currSw: ID of the current switch
     * @param switches: Set of switch IDs in the network
     * @param dfsList: HashMap of DFS data structure for each switch
     * @param currSet: Set of nodes in the current cluster in formation
     * @param clusters: Set of already formed clusters
     * @return long: DSF index to be used when a new node is visited
     */

    private long dfsTraverse (long parentIndex, long currIndex,
                              long currSw, Set<Long> switches,
                              Map<Long, ClusterDFS> dfsList, Set <Long> currSet,
                              Set <Cluster> clusters) {

        //Get the DFS object corresponding to the current switch
        ClusterDFS currDFS = dfsList.get(currSw);
        // Get all the links corresponding to this switch


        //Assign the DFS object with right values.
        currDFS.setVisited(true);
        currDFS.setDfsIndex(currIndex);
        currDFS.setParentDFSIndex(parentIndex);
        currIndex++;

        // Traverse the graph through every outgoing link.
        if (switchPorts.get(currSw) != null){
            for(Short p: switchPorts.get(currSw)) {
                Set<Link> lset = switchPortLinks.get(new NodePortTuple(currSw, p));
                if (lset == null) continue;
                for(Link l:lset) {
                    long dstSw = l.getDst();

                    // ignore incoming links.
                    if (dstSw == currSw) continue;

                    // ignore if the destination is already added to 
                    // another cluster
                    if (switchClusterMap.get(dstSw) != null) continue;

                    // ignore this link if it is in broadcast domain
                    if (isBroadcastDomainLink(l)) continue;

                    // Get the DFS object corresponding to the dstSw
                    ClusterDFS dstDFS = dfsList.get(dstSw);

                    if (dstDFS.getDfsIndex() < currDFS.getDfsIndex()) {
                        // could be a potential lowpoint
                        if (dstDFS.getDfsIndex() < currDFS.getLowpoint())
                            currDFS.setLowpoint(dstDFS.getDfsIndex());

                    } else if (!dstDFS.isVisited()) {
                        // make a DFS visit
                        currIndex = dfsTraverse(currDFS.getDfsIndex(), currIndex, dstSw,
                                                switches, dfsList, currSet, clusters);

                        if (currIndex < 0) return -1;

                        // update lowpoint after the visit
                        if (dstDFS.getLowpoint() < currDFS.getLowpoint())
                            currDFS.setLowpoint(dstDFS.getLowpoint());
                    }
                    // else, it is a node already visited with a higher
                    // dfs index, just ignore.
                }
            }
        }

        // Add current node to currSet.
        currSet.add(currSw);

        // Cluster computation.
        // If the node's lowpoint is greater than its parent's DFS index,
        // we need to form a new cluster with all the switches in the
        // currSet.
        if (currDFS.getLowpoint() > currDFS.getParentDFSIndex()) {
            // The cluster thus far forms a strongly connected component.
            // create a new switch cluster and the switches in the current
            // set to the switch cluster.
            Cluster sc = new Cluster();
            for(long sw: currSet){
                sc.add(sw);
                switchClusterMap.put(sw, sc);
            }
            // delete all the nodes in the current set.
            currSet.clear();
            // add the newly formed switch clusters to the cluster set.
            clusters.add(sc);
        }

        return currIndex;
    }

    public boolean isBroadcastDomainLink(Link l) {
        NodePortTuple n1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        NodePortTuple n2 = new NodePortTuple(l.getDst(), l.getDstPort());
        return (broadcastDomainPorts.contains(n1) ||
                broadcastDomainPorts.contains(n2));
    }

    public boolean isBroadcastDomainPort(NodePortTuple npt) {
        return broadcastDomainPorts.contains(npt);
    }

    class NodeDist implements Comparable<NodeDist> {
        private Long node;
        public Long getNode() {
            return node;
        }

        private int dist; 
        public int getDist() {
            return dist;
        }

        public NodeDist(Long node, int dist) {
            this.node = node;
            this.dist = dist;
        }

        public int compareTo(NodeDist o) {
            if (o.dist == this.dist) {
                return (int)(o.node - this.node);
            }
            return o.dist - this.dist;
        }
    }

    protected BroadcastTree dijkstra(Cluster c, Long dst, Map<Link, Integer> linkCost) {
        HashMap<Long, Link> nexthoplinks = new HashMap<Long, Link>();
        HashMap<Long, Long> nexthopnodes = new HashMap<Long, Long>();
        HashMap<Long, Integer> cost = new HashMap<Long, Integer>();
        int w;

        for (Long node: c.links.keySet()) {
            nexthoplinks.put(node, null);
            nexthopnodes.put(node, null);
            cost.put(node, MAX_PATH_WEIGHT);
        }

        HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
        PriorityQueue<NodeDist> nodeq = new PriorityQueue<NodeDist>();
        nodeq.add(new NodeDist(dst, 0));
        cost.put(dst, 0);
        while (nodeq.peek() != null) {
            NodeDist n = nodeq.poll();
            Long cnode = n.getNode();
            int cdist = n.getDist();
            if (cdist >= MAX_PATH_WEIGHT) break;
            if (seen.containsKey(cnode)) continue;
            seen.put(cnode, true);

            for (Link link: c.links.get(cnode)) {
                Long neighbor = link.getSrc();
                if (linkCost == null || linkCost.get(link)==null) w = 1;
                else w = linkCost.get(link);

                int ndist = cdist + w; // the weight of the link, always 1 in current version of floodlight.
                if (ndist < cost.get(neighbor)) {
                    cost.put(neighbor, ndist);
                    nexthoplinks.put(neighbor, link);
                    nexthopnodes.put(neighbor, cnode);
                    nodeq.add(new NodeDist(neighbor, ndist));
                }
            }
        }

        BroadcastTree ret = new BroadcastTree(nexthoplinks, cost);
        return ret;
    }

    protected void calculateShortestPathTreeInClusters() {
        pathcache.clear();
        destinationRootedTrees.clear();
        for(Cluster c: clusters) {
            for (Long node : c.links.keySet()) {
                BroadcastTree tree = dijkstra(c, node, null);
                destinationRootedTrees.put(node, tree);
            }
        }
    }

    protected void calculateBroadcastTreeInClusters() {
        for(Cluster c: clusters) {
            // c.id is the smallest node that's in the cluster
            BroadcastTree tree = destinationRootedTrees.get(c.id);
            clusterBroadcastTrees.put(c.id, tree);
        }
    }

    protected void calculateBroadcastNodePortsInClusters() {

        clusterBroadcastTrees.clear();

        calculateBroadcastTreeInClusters();

        for(Cluster c: clusters) {
            // c.id is the smallest node that's in the cluster
            BroadcastTree tree = clusterBroadcastTrees.get(c.id);
            clusterBroadcastTrees.put(c.id, tree);
            //log.info("Broadcast Tree {}", tree);

            Set<NodePortTuple> nptSet = new HashSet<NodePortTuple>();
            Map<Long, Link> links = tree.getLinks();
            if (links == null) continue;
            for(long nodeId: links.keySet()) {
                Link l = links.get(nodeId);
                if (l == null) continue;
                NodePortTuple npt1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
                NodePortTuple npt2 = new NodePortTuple(l.getDst(), l.getDstPort());
                nptSet.add(npt1);
                nptSet.add(npt2);
            }
            clusterBroadcastNodePorts.put(c.id, nptSet);
        }
    }

    private Route buildroute(RouteId id, long srcId, long dstId) {
        LinkedList<Link> path =  new LinkedList<Link>();

        if (destinationRootedTrees == null) return null;
        if (destinationRootedTrees.get(dstId) == null) return null;

        Map<Long, Link> nexthoplinks = destinationRootedTrees.get(dstId).getLinks();

        if (!switches.contains(srcId) || !switches.contains(dstId)) {
            // This is a switch that is not connected to any other switch
            // hence there was no update for links (and hence it is not in the network)
            log.debug("buildroute: Standalone switch: {}", srcId);

            // The only possible non-null path for this case is
            // if srcId equals dstId --- and that too is an 'empty' path []

        } else if ((nexthoplinks!=null) && (nexthoplinks.get(srcId)!=null)) {
            while (srcId != dstId) {
                Link l = nexthoplinks.get(srcId);
                path.addLast(l);
                srcId = nexthoplinks.get(srcId).getDst();
            }
        }
        // else, no path exists, and path equals null

        Route result = null;
        if (path != null && !path.isEmpty()) result = new Route(id, path);
        if (log.isTraceEnabled()) {
            log.trace("buildroute: {}", result);
        }
        return result;
    }

    protected int getCost(long srcId, long dstId) {
        BroadcastTree bt = destinationRootedTrees.get(dstId);
        if (bt == null) return -1;
        return (bt.getCost(srcId));
    }

    /* 
     * Getter Functions
     */

    protected Set<Cluster> getClusters() {
        return clusters;
    }

    // IRoutingEngineService interfaces
    protected boolean routeExists(long srcId, long dstId) {
        BroadcastTree bt = destinationRootedTrees.get(dstId);
        if (bt == null) return false;
        Link link = bt.getLinks().get(srcId);
        if (link == null) return false;
        return true;
    }

    protected Route getRoute(long srcId, long dstId) {
        RouteId id = new RouteId(srcId, dstId);
        Route result = null;
        if (pathcache.containsKey(id)) {
            result = pathcache.get(id);
        } else {
            result = buildroute(id, srcId, dstId);
            pathcache.put(id, result);
        }
        if (log.isTraceEnabled()) {
            log.trace("getRoute: {} -> {}", id, result);
        }
        return result;
    }

    protected BroadcastTree getBroadcastTreeForCluster(long clusterId){
        Cluster c = switchClusterMap.get(clusterId);
        if (c == null) return null;
        return clusterBroadcastTrees.get(c.id);
    }

    // 
    //  ITopologyService interface method helpers.
    // 

    protected boolean isInternal(long switchid, short port) {
        NodePortTuple npt = new NodePortTuple(switchid, port);
        if (switchPortLinks.containsKey(npt)) return true;
        return false;
    }

    protected long getSwitchClusterId(long switchId) {
        Cluster c = switchClusterMap.get(switchId);
        if (c == null) return switchId;
        return c.getId();
    }

    protected Set<Long> getSwitchesInCluster(long switchId) {
        Cluster c = switchClusterMap.get(switchId);
        if (c == null) return null;
        return (c.getNodes());
    }

    protected boolean inSameCluster(long switch1, long switch2) {
        Cluster c1 = switchClusterMap.get(switch1);
        Cluster c2 = switchClusterMap.get(switch2);
        if (c1 != null && c2 != null)
            return (c1.getId() == c2.getId());
        return (switch1 == switch2);
    }

    protected boolean
    isIncomingBroadcastAllowedOnSwitchPort(long sw, short portId) {
        if (isInternal(sw, portId)) {
            long clusterId = getSwitchClusterId(sw);
            NodePortTuple npt = new NodePortTuple(sw, portId);
            if (clusterBroadcastNodePorts.get(clusterId).contains(npt))
                return true;
            else return false;
        }
        return true;
    }

    protected Set<NodePortTuple>
    getBroadcastNodePortsInCluster(long sw) {
        long clusterId = getSwitchClusterId(sw);
        return clusterBroadcastNodePorts.get(clusterId);
    }

    public boolean isInSameBroadcastDomain(long s1, short p1, long s2, short p2) {
        return false;
    }

    public NodePortTuple getOutgoingSwitchPort(long src, short srcPort,
                                               long dst, short dstPort) {
        // Use this function to redirect traffic if needed.
        return new NodePortTuple(dst, dstPort);
    }

    public Set<Long> getSwitches() {
        return switches;
    }

    public Set<Short> getPorts(long sw) {
        return switchPorts.get(sw);
    }

    public Set<Short> getBroadcastPorts(long targetSw, long src, short srcPort) {
        Set<Short> result = new HashSet<Short>();
        long clusterId = getSwitchClusterId(targetSw);
        for(NodePortTuple npt: clusterBroadcastNodePorts.get(clusterId)) {
            if (npt.getNodeId() == targetSw) {
                result.add(npt.getPortId());
            }
        }
        return result;
    }
}
