/*
 * Graph.java Copyright (C) 2019. Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * @version $Id: Graph.java,v 1.51 2008-10-10 08:42:37 huson Exp $
 *
 * @author Daniel Huson
 *
 */
package jloda.graph;

import jloda.util.Basic;
import jloda.util.IteratorAdapter;
import jloda.util.parse.NexusStreamParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * A graph
 * <p/>
 * The nodes and edges are stored in several doubly-linked lists.
 * The set of nodes in the graph is stored in a list
 * The set of edges in the graph is stored in a list
 * Around each node, the set of incident edges is stored in a list.
 * Daniel Huson, 2002
 * <p/>
 */
public class Graph extends GraphBase {
    protected final NodeArray<String> nodeLabels;
    protected final EdgeArray<String> edgeLabels;
    private Node firstNode;
    private Node lastNode;
    private int numberNodes;
    private int numberOfNodesThatAreHidden;
    private int idsNodes; // number of ids assigned to nodes

    private Edge firstEdge;
    protected Edge lastEdge;
    private int numberEdges;
    private int numberOfEdgesThatAreHidden;
    private int idsEdges; // number of ids assigned to edges

    private boolean ignoreGraphHasChanged = false; // set this when we are deleting a whole graph

    private final List<GraphUpdateListener> graphUpdateListeners = new LinkedList<>();  //List of listeners that are fired when the graph changes.
    final EdgeSet specialEdges;

    private final List<WeakReference<NodeSet>> nodeSets = new LinkedList<>();
    // created node arrays are kept here. When an node is deleted, it's
    // entry in all node arrays is set to null
    private final List<WeakReference<NodeAssociation>> nodeAssociations = new LinkedList<>();

    // created edge arrays are kept here. When an edge is deleted, it's
    // entry in all edge arrays is set to null
    private final List<WeakReference<EdgeAssociation>> edgeAssociations = new LinkedList<>();
    // keep track of edge sets
    private final List<WeakReference<EdgeSet>> edgeSets = new LinkedList<>();
    private String name;

    /**
     * Constructs a new empty graph.
     */
    public Graph() {
        setOwner(this);
        specialEdges = new EdgeSet(this);
        nodeLabels = new NodeArray<>(this);
        edgeLabels = new EdgeArray<>(this);
    }

    /**
     * Constructs a new node of the type used in the graph. This does not add the node to the graph
     * structure
     *
     * @return Node a new node
     */
    public Node newNode() {
        return newNode(null);
    }

    /**
     * Constructs a new node and set its info to obj.  This does not add the node to the graph
     * structure
     *
     * @param obj the info object
     * @return Node a new node
     */
    public Node newNode(Object obj) {
        return new Node(this, obj);
    }

    /**
     * Adds a node to the graph. The information in the node is replaced with obj. The node
     * is added to the end of the list of nodes.
     *
     * @param info the info object
     * @param v    the new node
     */
    void registerNewNode(Object info, Node v) {
        v.init(this, lastNode, null, ++idsNodes, info);
        if (firstNode == null)
            firstNode = v;
        if (lastNode != null)
            lastNode.next = v;
        lastNode = v;
        numberNodes++;
    }

    /**
     * sets the hidden state of a node. Hidden nodes are not returned by node iterators
     *
     * @param v
     * @param hide
     * @return true, if hidden state changed
     */
    public boolean setHidden(Node v, boolean hide) {
        if (hide) {
            if (!v.isHidden()) {
                v.setHidden(true);
                numberOfNodesThatAreHidden++;
                return true;
            }
        } else {
            if (v.isHidden()) {
                v.setHidden(false);
                numberOfNodesThatAreHidden--;
                return true;
            }
        }
        return false;
    }

    /**
     * returns hidden state of a node
     *
     * @param v
     * @return true, if hidden
     */
    public boolean isHidden(Node v) {
        return v.isHidden();
    }


    /**
     * sets the hidden state of a edge. Hidden edges are not returned by edge iterators
     *
     * @param e
     * @param hide
     * @return true, if hidden state changed
     */
    public boolean setHidden(Edge e, boolean hide) {
        if (hide) {
            if (!e.isHidden()) {
                e.setHidden(true);
                numberOfEdgesThatAreHidden++;
                return true;
            }
        } else {
            if (e.isHidden()) {
                e.setHidden(false);
                numberOfEdgesThatAreHidden--;
                return true;
            }
        }
        return false;
    }

    /**
     * returns hidden state of a edge
     *
     * @param e
     * @return true, if hidden
     */
    public boolean isHidden(Edge e) {
        return e.isHidden();
    }

    /**
     * Constructs a new edge between nodes v and w. This edge is not added to the graph.
     *
     * @param v source node
     * @param w target node
     * @return a new edge between nodes v and w
     */
    public Edge newEdge(Node v, Node w) throws IllegalSelfEdgeException {
        return new Edge(this, v, w);
    }

    /**
     * Constructs a new edge between nodes v and w and sets its info to obj. This edge is not added to the graph.
     *
     * @param v   source node
     * @param w   target node
     * @param obj the info object
     * @return a new edge between nodes v and w and sets its info to obj
     */
    public Edge newEdge(Node v, Node w, Object obj) throws IllegalSelfEdgeException {
        return new Edge(this, v, w, obj);
    }

    /**
     * Constructs a new edge between nodes v and w. The edge is inserted into the list of edges incident with
     * v and the list of edges incident with w. The place it is inserted into these list for edges
     * incident with v is determined by e_v and dir_v: if dir_v = Edge.AFTER then it is inserted after
     * e_v in the list, otherwise it is inserted before e_v. Likewise for the list of edges incident with w.
     * <p/>
     * The info is set using the obj.
     *
     * @param v     source node
     * @param e_v   reference edge incident to v
     * @param w     target node
     * @param e_w   reference edge incident to w
     * @param dir_v before or after reference e_v
     * @param dir_w before or after reference e_w
     * @param obj   the info object
     * @return a new edge
     */
    public Edge newEdge(Node v, Edge e_v, Node w, Edge e_w,
                        int dir_v, int dir_w, Object obj) throws IllegalSelfEdgeException {
        return new Edge(this, v, e_v, w, e_w, dir_v, dir_w, obj);
    }

    /**
     * Adds a  edge to the graph. The edge is inserted into the list of edges incident with
     * v and the list of edges incident with w. The place it is inserted into these list for edges
     * incident with v is determined by e_v and dir_v: if dir_v = Edge.AFTER then it is inserted after
     * e_v in the list, otherwise it is inserted before e_v. Likewise for the list of edges incident with w.
     * <p/>
     * The info is set using the obj.
     *
     * @param v     source
     * @param e_v   reference source edge
     * @param w     target
     * @param e_w   reference target edge
     * @param dir_v insert before/after source reference edge
     * @param dir_w insert before/after target reference edge
     * @param obj   info object
     * @param e     the new edge
     * @
     */
    void registerNewEdge(Node v, Edge e_v, Node w, Edge e_w, int dir_v, int dir_w, Object obj, Edge e) {
        checkOwner(v);
        checkOwner(w);
        v.incrementOutDegree();
        w.incrementInDegree();

        e.init(this, ++idsEdges, v, e_v, dir_v, w, e_w, dir_w, obj);
        if (firstEdge == null)
            firstEdge = e;
        if (lastEdge != null)
            lastEdge.next = e;
        lastEdge = e;
        numberEdges++;
    }

    /**
     * Removes edge e from the graph.
     *
     * @param e the edge
     */
    public void deleteEdge(Edge e) {
        checkOwner(e);
        // note: firstEdge and lastEdge are set in unregisterEdge
        e.deleteEdge();
    }

    /**
     * called from edge when being deleted
     *
     * @param e
     */
    void unregisterEdge(Edge e) {
        checkOwner(e);
        if (e.isHidden())
            numberOfEdgesThatAreHidden--;
        deleteEdgeFromArrays(e);
        deleteEdgeFromSets(e);

        getSource(e).decrementOutDegree();
        getTarget(e).decrementInDegree();
        if (firstEdge == e)
            firstEdge = (Edge) e.next;
        if (lastEdge == e)
            lastEdge = (Edge) e.prev;
        numberEdges--;
        if (numberEdges == 0)
            idsEdges = 0;
    }

    /**
     * Removes node v from the graph.
     *
     * @param v the node
     */
    public void deleteNode(Node v) {
        // note: firstNode and lastNode are set in unregisterNode
        v.deleteNode();
    }

    /**
     * called from node when being deleted
     *
     * @param v
     */
    void unregisterNode(Node v) {
        checkOwner(v);
        deleteNodeFromArrays(v);
        deleteNodeFromSets(v);
        if (v.isHidden())
            numberOfNodesThatAreHidden--;

        if (firstNode == v)
            firstNode = (Node) v.next;
        if (lastNode == v)
            lastNode = (Node) v.prev;
        numberNodes--;
        if (numberNodes == 0)
            idsNodes = 0;
    }

    /**
     * Deletes all edges.
     */
    public void deleteAllEdges() {
        while (firstEdge != null)
            deleteEdge(firstEdge);
    }

    /**
     * Deletes all nodes.
     */
    public void deleteAllNodes() {
        ignoreGraphHasChanged = true;
        while (firstNode != null) {
            deleteNode(firstNode);
        }
        ignoreGraphHasChanged = false;
    }

    /**
     * Clears the graph.
     */
    public void clear() {
        deleteAllNodes();
    }

    /**
     * Change the order of edges adjacent to a node.
     *
     * @param v        the node in question.
     * @param newOrder the desired sequence of edges.
     */
    public void rearrangeAdjacentEdges(Node v, List<Edge> newOrder) {
        checkOwner(v);
        v.rearrangeAdjacentEdges(newOrder);
    }

    /**
     * move the node to the front of the list of nodes
     *
     * @param v
     */
    public void moveToFront(Node v) {
        if (v != null && v != firstNode) {
            checkOwner(v);
            if (v.prev != null)
                v.prev.next = v.next;
            if (v.next != null)
                v.next.prev = v.prev;
            v.prev = null;
            Node w = firstNode;
            firstNode = v;
            v.next = w;
            if (w != null)
                w.prev = v;
            fireGraphHasChanged();
        }
    }

    /**
     * move the node to the bacl of the list of nodes
     *
     * @param v
     */
    public void moveToBack(Node v) {
        if (v != null && v != lastNode) {
            checkOwner(v);
            if (v.prev != null)
                v.prev.next = v.next;
            if (v.next != null)
                v.next.prev = v.prev;
            v.prev = null;
            Node w = lastNode;
            lastNode = v;
            v.prev = w;
            if (w != null)
                w.next = v;
            fireGraphHasChanged();
        }
    }

    /**
     * move the edge to the front of the list of edges
     *
     * @param e
     */
    public void moveToFront(Edge e) {
        if (e != null && e != firstEdge) {
            checkOwner(e);
            if (e.prev != null)
                e.prev.next = e.next;
            if (e.next != null)
                e.next.prev = e.prev;
            e.prev = null;
            Edge f = firstEdge;
            firstEdge = e;
            e.next = f;
            if (f != null)
                f.prev = e;
            fireGraphHasChanged();
        }
    }

    /**
     * move the edge to the back of the list of edges
     *
     * @param e
     */
    public void moveToBack(Edge e) {
        if (e != null && e != lastEdge) {
            checkOwner(e);
            if (e.prev != null)
                e.prev.next = e.next;
            if (e.next != null)
                e.next.prev = e.prev;
            e.prev = null;
            Edge f = lastEdge;
            lastEdge = f;
            e.prev = f;
            if (f != null)
                f.next = e;
            fireGraphHasChanged();
        }
    }

    /**
     * Returns the node opposite node v via edge e.
     *
     * @param v the node
     * @param e the edge
     * @return the opposite node
     * Better: use v.getOpposite(e) or e.getOpposite(v)
     */
    public Node getOpposite(Node v, Edge e) {
        checkOwner(e);
        return e.getOpposite(v);
    }

    /**
     * Get the first adjacent edge to v.
     *
     * @param v the node
     * @return the first adjacent edge
     *  Better: use v.getFirstAdjacentEdge()
     */
    public Edge getFirstAdjacentEdge(Node v) {
        checkOwner(v);
        return v.getFirstAdjacentEdge();
    }

    /**
     * Get the last adjacent edge to v.
     *
     * @param v the node
     * @return the last adjacent edge
     * Better: use v.getLastAdjacentEdge()
     */
    public Edge getLastAdjacentEdge(Node v) {
        checkOwner(v);
        return v.getLastAdjacentEdge();
    }


    /**
     * Get the successor of e adjacent to v
     *
     * @param e the edge
     * @param v the node
     * @return the successor of edge adjacent to v
     *  Better: use v.getNextAdjacentEdge(e)
     */
    public Edge getNextAdjacentEdge(Edge e, Node v) {
        checkOwner(v);
        return v.getNextAdjacentEdge(e);
    }

    /**
     * Get the predecessor of e adjacent to v
     *
     * @param e the edge
     * @param v the node
     * @return the predecessor of edge adjacent to v
     *  Better: use v.getPrevAdjacentEdge(e)
     */
    public Edge getPrevAdjacentEdge(Edge e, Node v) {
        checkOwner(v);
        return v.getPrevAdjacentEdge(e);
    }

    /**
     * Get the cyclic successor of e adjacent to v.
     *
     * @param e the edge
     * @param v the node
     * @return the cyclic successor of edge adjacent to v
     *  Better: use v.getNextAdjacentEdgeCyclic(e)
     */
    public Edge getNextAdjacentEdgeCyclic(Edge e, Node v) {
        checkOwner(v);
        return v.getNextAdjacentEdgeCyclic(e);
    }

    /**
     * Get the cyclic predecessor of e adjacent to v.
     *
     * @param e the edge
     * @param v the node
     * @return the cyclic predecessor of edge adjacent to v
     *  Better: use v.getPrevAdjacentEdgeCyclic(e)
     */
    public Edge getPrevAdjacentEdgeCyclic(Edge e, Node v) {
        checkOwner(v);
        return v.getPrevAdjacentEdgeCyclic(e);
    }

    /**
     * Get the first edge in the graph.
     *
     * @return the first edge
     */
    public Edge getFirstEdge() {
        if (firstEdge != null && firstEdge.isHidden()) {
            return firstEdge.getNext();
        }
        return firstEdge;
    }

    /**
     * Get the last edge in the graph.
     *
     * @return the last edge
     */
    public Edge getLastEdge() {
        if (lastEdge != null && lastEdge.isHidden())
            return lastEdge.getPrev();
        return lastEdge;
    }

    /**
     * Get the successor of edge e.
     *
     * @param e edge
     * @return the successor edge
     */
    public Edge getNextEdge(Edge e) {
        checkOwner(e);
        return e.getNext();
    }

    /**
     * Get the predecessor of edge e.
     *
     * @param e edge
     * @return the predecessor edge
     */
    public Edge getPrevEdge(Edge e) {
        checkOwner(e);
        return e.getPrev();
    }

    /**
     * Get an edge between the two nodes v and w, if it exists
     *
     * @param v source node
     * @param w target node
     * @return an edge between v and w
     */
    public Edge getCommonEdge(Node v, Node w) {
        checkOwner(v);
        return v.getCommonEdge(w);
    }

    /**
     * Get the first node in the graph.
     *
     * @return the first node
     */
    public Node getFirstNode() {
        if (firstNode != null && firstNode.isHidden())
            return firstNode.getNext();
        return firstNode;
    }

    /**
     * Get the last node in the graph.
     *
     * @return the last node
     */
    public Node getLastNode() {
        if (lastNode != null && lastNode.isHidden())
            return lastNode.getPrev();
        return lastNode;
    }

    /**
     * Get the successor node of v
     *
     * @param v the node
     * @return the successor node
     */
    public Node getNextNode(Node v) {
        checkOwner(v);
        return v.getNext();
    }

    /**
     * Get the predecessor of v.
     *
     * @param v the node
     * @return the predecessor node
     */
    public Node getPrevNode(Node v) {
        checkOwner(v);
        return v.getPrev();
    }

    /**
     * Get the number of nodes.
     *
     * @return the number of nodes
     */
    public int getNumberOfNodes() {
        return numberNodes - numberOfNodesThatAreHidden;
    }

    /**
     * Get number of edges.
     *
     * @return the number of edges
     */
    public int getNumberOfEdges() {
        return numberEdges - numberOfEdgesThatAreHidden;
    }

    /**
     * Get the source node of e.
     *
     * @param e the edge
     * @return the source of e
     * Better: use e.getSource()
     */
    public Node getSource(Edge e) {
        checkOwner(e);
        return e.getSource();
    }

    /**
     * Get the target node of e.
     *
     * @param e the edge
     * @return the target of e
     * Better: use e.getTarget()
     */
    public Node getTarget(Edge e) {
        checkOwner(e);
        return e.getTarget();
    }

    /**
     * Get the degree of node v.
     *
     * @return the degree
     * Better: use v.getDegree()
     */
    public int getDegree(Node v) {
        checkOwner(v);
        return v.getDegree();
    }

    /**
     * Get the in-degree of node v.
     *
     * @return the in-degree
     * Better: use v.getInDegree()
     */
    public int getInDegree(Node v) {
        checkOwner(v);
        return v.getInDegree();
    }

    /**
     * Get the out-degree of node v.
     *
     * @return the out-degree
     * Better: use v.getOutDegree()
     */
    public int getOutDegree(Node v) {
        checkOwner(v);
        return v.getOutDegree();
    }

    /**
     * Get an iterator over all edges
     *
     * @return edge iterator
     * Better: use edges()
     */
    public Iterator<Edge> edgeIterator() {
        return new IteratorAdapter<>() {
            private Edge e = getFirstEdge();

            protected Edge findNext() throws NoSuchElementException {
                if (e != null) {
                    final Edge result = e;
                    e = getNextEdge(e);
                    return result;
                } else {
                    throw new NoSuchElementException("at end");
                }
            }
        };
    }

    /**
     * Get an iterator over all edges, including hidden ones
     *
     * @return edge iterator
     */
    public Iterator<Edge> edgeIteratorIncludingHidden() {
        return new IteratorAdapter<>() {
            private Edge e = firstEdge;

            protected Edge findNext() throws NoSuchElementException {
                if (e != null) {
                    final Edge result = e;
                    checkOwner(e);
                    e = (Edge) result.next;
                    return result;
                } else {
                    throw new NoSuchElementException("at end");
                }
            }
        };
    }

    /**
     * Get an iterator over all nodes
     *
     * @return node iterator
     * Better: use nodes()
     */
    public Iterator<Node> nodeIterator() {
        return new IteratorAdapter<>() {
            private Node v = getFirstNode();

            protected Node findNext() throws NoSuchElementException {
                if (v != null) {
                    final Node result = v;
                    v = getNextNode(v);
                    return result;
                } else {
                    throw new NoSuchElementException("at end");
                }
            }

            public boolean hasNext() {
                return v != null;
            }
        };
    }

    /**
     * Get an iterator over all nodes
     *
     * @return node iterator
     */
    public Iterator<Node> nodeIteratorIncludingHidden() {
        return new IteratorAdapter<>() {
            private Node v = firstNode;

            protected Node findNext() throws NoSuchElementException {
                if (v != null) {
                    final Node result = v;
                    v = (Node) v.next;
                    return result;
                } else {
                    throw new NoSuchElementException("at end");
                }
            }

            public boolean hasNext() {
                return v != null;
            }
        };
    }

    /**
     * Get an iterator over all nodes adjacent to v.
     *
     * @param v node
     * @return all nodes adjacent to v
     * Better: use v.adjacentNodes()
     */
    public Iterable<Node> adjacentNodes(Node v) {
        return v.adjacentNodes();
    }

    /**
     * Get an iterator over all edges adjacent to v.
     *
     * @param v node
     * @return all edges adjacent to v
     * Better: use v.adjacentEdges()
     */
    public Iterable<Edge> adjacentEdges(Node v) {
        return v.adjacentEdges();
    }

    /**
     * Get an iterator over all all in-edges adjacent to v.
     *
     * @param v node
     * @return all in-edges adjacent to v
     * Better: use v.inEdges()
     */
    public Iterable<Edge> inEdges(Node v) {
        return v.inEdges();
    }

    /**
     * Get an iterator over all all out-edges adjacent to v.
     *
     * @param v node
     * @return all out-edges adjacent to v
     * Better: use v.outEdges()
     */
    public Iterable<Edge> outEdges(Node v) {
        return v.outEdges();
    }

    /**
     * Get the id of node v.
     *
     * @param v node
     * @return the id
     */
    public int getId(Node v) {
        checkOwner(v);
        return v.getId();
    }

    /**
     * Get the id of edge e.
     *
     * @param e edge
     * @return the id
     */
    public int getId(Edge e) {
        checkOwner(e);
        return e.getId();
    }

    /**
     * Get a string representation of the graph.
     *
     * @return the string
     */
    public String toString() {
        StringBuilder buf = new StringBuilder("Graph:\n");
        buf.append("Nodes: ").append(getNumberOfNodes()).append("\n");

        for (Node v = getFirstNode(); v != null; v = getNextNode(v))
            buf.append(v.toString()).append("\n");
        buf.append("Edges: ").append(getNumberOfEdges()).append("\n");
        for (Edge e = getFirstEdge(); e != null; e = getNextEdge(e))
            buf.append(e.toString()).append("\n");

        return buf.toString();
    }

    /**
     * Get the info associated with node v.
     *
     * @param v node
     * @return the info
     */
    public Object getInfo(Node v) {
        checkOwner(v);
        return v.getInfo();
    }

    /**
     * set the info associated with node v.
     *
     * @param v   node
     * @param obj the info object
     */
    public void setInfo(Node v, Object obj) {
        checkOwner(v);
        v.setInfo(obj);
    }

    /**
     * Get the info associated with edge e.
     *
     * @param e edge
     * @return the info
     */
    public Object getInfo(Edge e) {
        checkOwner(e);
        return e.getInfo();
    }

    /**
     * set the info associated with edge e.
     *
     * @param e   edge
     * @param obj the info object
     */
    public void setInfo(Edge e, Object obj) {
        checkOwner(e);
        e.setInfo(obj);
    }

    /**
     * Get an edge directed from one given node to another, if it exists.
     *
     * @param v source node
     * @param w target node
     * @return edge from v tp w, if it exists, else null
     */
    public Edge findDirectedEdge(Node v, Node w) {
        checkOwner(v);
        return v.findDirectedEdge(w);
    }

    /**
     * Adds a GraphUpdateListener
     *
     * @param graphUpdateListener the listener to be added
     */
    public void addGraphUpdateListener(GraphUpdateListener graphUpdateListener) {
        graphUpdateListeners.add(graphUpdateListener);
    }

    /**
     * Removes a GraphUpdateListener
     *
     * @param graphUpdateListener the listener to be removed
     */
    public void removeGraphUpdateListener
    (GraphUpdateListener graphUpdateListener) {
        graphUpdateListeners.remove(graphUpdateListener);
    }

    /* Fires the newNode event for all GraphUpdateListeners
    *@param v the node
    */

    protected void fireNewNode(Node v) {
        checkOwner(v);

        for (GraphUpdateListener gul : graphUpdateListeners) {
            gul.newNode(v);
        }
    }

    /* Fires the deleteNode event for all GraphUpdateListeners
    *@param v the node
    */

    protected void fireDeleteNode(Node v) {
        checkOwner(v);

        for (GraphUpdateListener gul : graphUpdateListeners) {
            gul.deleteNode(v);
        }
    }

    /* Fires the newEdge event for all GraphUpdateListeners
    *@param e the edge
    */

    protected void fireNewEdge(Edge e) {
        checkOwner(e);

        for (GraphUpdateListener gul : graphUpdateListeners) {
            gul.newEdge(e);
        }
    }

    /* Fires the deleteEdge event for all GraphUpdateListeners
    *@param e the edge
    */

    protected void fireDeleteEdge(Edge e) {
        checkOwner(e);

        for (GraphUpdateListener gul : graphUpdateListeners) {
            gul.deleteEdge(e);
        }
    }

    /* Fires the graphHasChanged event for all GraphUpdateListeners
    */

    protected void fireGraphHasChanged() {
        if (!ignoreGraphHasChanged) {

            for (GraphUpdateListener gul : graphUpdateListeners) {
                gul.graphHasChanged();
            }
        }
    }

    /*
     * Fires the graphWasRead event for all GraphUpdateListeners
    */

    protected void fireGraphRead(NodeSet nodes, EdgeSet edges) {
        checkOwner(nodes);
        checkOwner(edges);
        GraphUpdateListener[] a = graphUpdateListeners.toArray(new GraphUpdateListener[0]);
        for (GraphUpdateListener gul : a) {
            gul.graphWasRead(nodes, edges);
        }
    }

    /**
     * copies one graph onto another
     *
     * @param src the source graph
     */
    public void copy(Graph src) {
        copy(src, null, null);
    }

    /**
     * Copies one graph onto another. Maintains the ids of nodes and edges
     *
     * @param src             the source graph
     * @param oldNode2newNode if not null, returns map: old node id onto new node id
     * @param oldEdge2newEdge if not null, returns map: old edge id onto new edge id
     */
    public void copy(Graph src, NodeAssociation<Node> oldNode2newNode, EdgeAssociation<Edge> oldEdge2newEdge) {
        clear();

        if (oldNode2newNode == null)
            oldNode2newNode = new NodeArray<>(src);
        if (oldEdge2newEdge == null)
            oldEdge2newEdge = new EdgeArray<>(src);

        for (Node v = src.getFirstNode(); v != null; v = src.getNextNode(v)) {
            Node w = newNode();
            w.setId(v.getId());
            setInfo(w, src.getInfo(v));
            oldNode2newNode.put(v, w);
        }
        idsNodes = src.idsNodes;

        for (Edge e = src.getFirstEdge(); e != null; e = src.getNextEdge(e)) {
            Node p = oldNode2newNode.getValue(src.getSource(e));
            Node q = oldNode2newNode.getValue(src.getTarget(e));
            Edge f = null;
            try {
                f = newEdge(p, q);
                f.setId(e.getId());
            } catch (IllegalSelfEdgeException e1) {
                Basic.caught(e1);
            }
            if (src.isSpecial(e))
                setSpecial(f, true);
            setInfo(f, src.getInfo(e));
            oldEdge2newEdge.put(e, f);
        }
        idsEdges = src.idsEdges;

        // change all adjacencies to reflect order in old graph:
        for (Node v = src.getFirstNode(); v != null; v = src.getNextNode(v)) {
            Node w = oldNode2newNode.getValue(v);
            List<Edge> newOrder = new LinkedList<>();
            for (Edge e = v.getFirstAdjacentEdge(); e != null; e = v.getNextAdjacentEdge(e)) {
                newOrder.add(oldEdge2newEdge.getValue(e));
            }
            w.rearrangeAdjacentEdges(newOrder);
        }
    }

    /**
     * Copies one graph onto another. Maintains the ids of nodes and edges
     *
     * @param src             the source graph
     * @param srcNodes        the nodes of the source graph to be copied
     * @param oldNode2newNode if not null, returns map: old node id onto new node id
     * @param oldEdge2newEdge if not null, returns map: old edge id onto new edge id
     */
    public void copy(Graph src, Set<Node> srcNodes, Map<Node, Node> oldNode2newNode, HashMap<Edge, Edge> oldEdge2newEdge) {
        clear();

        if (oldNode2newNode == null)
            oldNode2newNode = new HashMap<>();
        if (oldEdge2newEdge == null)
            oldEdge2newEdge = new HashMap<>();

        final Set<Edge> edges = new HashSet<>();// don't used an edge set here in multi-thread use

        for (Node v : srcNodes) {
            Node w = newNode();
            w.setId(v.getId());
            setInfo(w, src.getInfo(v));
            oldNode2newNode.put(v, w);
            for (Edge e : v.outEdges())
                edges.add(e);
        }

        for (Edge e : edges) {
            final Node p = oldNode2newNode.get(e.getSource());
            final Node q = oldNode2newNode.get(e.getTarget());
            Edge f = null;
            try {
                f = newEdge(p, q);
                f.setId(e.getId());
            } catch (IllegalSelfEdgeException e1) {
                Basic.caught(e1);
            }
            if (src.isSpecial(e))
                setSpecial(f, true);
            setInfo(f, src.getInfo(e));
            oldEdge2newEdge.put(e, f);
        }

        // change all adjacencies to reflect order in old graph:
        for (Node v : srcNodes) {
            final Node w = oldNode2newNode.get(v);
            final List<Edge> newOrder = new LinkedList<>();
            for (Edge e : v.adjacentEdges()) {
                newOrder.add(oldEdge2newEdge.get(e));
            }
            w.rearrangeAdjacentEdges(newOrder);
        }
    }

    /**
     * iterates over all node labels
     *
     * @return node labels
     */
    public Iterable<String> nodeLabels() {
        return () -> new Iterator<>() {
            private Node v = getFirstNode();

            {
                while (v != null && getLabel(v) == null) {
                    v = v.getNext();
                }
            }

            @Override
            public boolean hasNext() {
                return v != null;
            }

            @Override
            public String next() {
                final String result = getLabel(v);
                {
                    do {
                        v = v.getNext();
                    }
                    while (v != null && getLabel(v) == null);
                }
                return result;
            }
        };
    }

    /**
     * iterates over all edge labels
     *
     * @return edge labels
     */
    public Iterable<String> edgeLabels() {
        return () -> new Iterator<>() {
            private Edge e = getFirstEdge();

            {
                while (e != null && getLabel(e) == null) {
                    e = e.getNext();
                }
            }

            @Override
            public boolean hasNext() {
                return e != null;
            }

            @Override
            public String next() {
                final String result = getLabel(e);
                {
                    do {
                        e = e.getNext();
                    }
                    while (e != null && getLabel(e) == null);
                }
                return result;
            }
        };
    }

    /**
     * Sets the label of an edge.
     *
     * @param e   Edge
     * @param lab String
     */
    public void setLabel(Edge e, String lab) {
        edgeLabels.put(e, lab);
    }

    /**
     * Gets the label of an edge.
     *
     * @param e Edge
     * @return edgeLabels String
     */
    public String getLabel(Edge e) {
        return edgeLabels.getValue(e);
    }

    /**
     * Sets the label of a node.
     *
     * @param v   Node
     * @param str String
     */
    public void setLabel(Node v, String str) {
        nodeLabels.setValue(v, str);
    }

    /**
     * Gets the taxon label of a node.
     *
     * @param v Node
     * @return nodeLabels String
     */
    public String getLabel(Node v) {
        return nodeLabels.getValue(v);
    }

    /**
     * produces a clone of this graph
     *
     * @return a clone of this graph
     */
    public Object clone() {
        Graph result = new Graph();
        result.copy(this);
        return result;
    }

    /**
     * determines whether two nodes are adjacent
     *
     * @param a
     * @param b
     * @return true, if adjacent
     * Better: use a.isAdjacent(b)
     */
    public boolean areAdjacent(Node a, Node b) {
        checkOwner(a);
        return a.isAdjacent(b);
    }

    /**
     * called from constructor of NodeAssociation to register with graph
     *
     * @param array
     */
    void registerNodeAssociation(NodeAssociation array) {
        synchronized (nodeAssociations) {
            final List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<NodeAssociation> ref : nodeAssociations) {
                if (ref.get() == null)
                    toDelete.add(ref); // reference is dead
            }
            if (toDelete.size() > 0)
                nodeAssociations.removeAll(toDelete);
            nodeAssociations.add(new WeakReference<>(array));
        }
    }

    /**
     * called from deleteNode to clean all array entries for the node
     *
     * @param v
     */
    void deleteNodeFromArrays(Node v) {
        checkOwner(v);
        synchronized (nodeAssociations) {
            List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<NodeAssociation> ref : nodeAssociations) {
                NodeAssociation<?> as = ref.get();
                if (as == null)
                    toDelete.add(ref); // reference is dead
                else {
                    as.put(v, null);
                }
            }
            for (WeakReference ref : toDelete) {
                nodeAssociations.remove(ref);
            }
        }
    }

    /**
     * called from constructor of NodeSet to register with graph
     *
     * @param set
     */
    void registerNodeSet(NodeSet set) {
        synchronized (nodeSets) {
            final List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<NodeSet> ref : nodeSets) {
                if (ref.get() == null)
                    toDelete.add(ref); // reference is dead
            }
            if (toDelete.size() > 0)
                nodeSets.removeAll(toDelete);
            nodeSets.add(new WeakReference<>(set));
        }
    }

    /**
     * called from deleteNode to clean all array entries for the node
     *
     * @param v
     */
    private void deleteNodeFromSets(Node v) {
        checkOwner(v);
        synchronized (nodeSets) {
            final List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<NodeSet> ref : nodeSets) {
                if (ref != null) {
                    final NodeSet set = ref.get();
                    if (set == null)
                        toDelete.add(ref); // reference is dead
                    else {
                        set.remove(v);
                    }
                }
            }
            for (WeakReference ref : toDelete) {
                nodeSets.remove(ref);
            }
        }
    }

    /**
     * called from constructor of EdgeAssociation to register with graph
     *
     * @param array
     */
    void registerEdgeAssociation(EdgeAssociation array) {
        synchronized (edgeAssociations)
        {
            final List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<EdgeAssociation> ref : edgeAssociations) {
                if (ref.get() == null)
                    toDelete.add(ref); // reference is dead
            }
            if (toDelete.size() > 0)
                edgeAssociations.removeAll(toDelete);
        }
        edgeAssociations.add(new WeakReference<>(array));
    }

    /**
     * called from deleteEdge to clean all array entries for the edge
     *
     * @param edge
     */
    void deleteEdgeFromArrays(Edge edge) {
        checkOwner(edge);
        synchronized (edgeAssociations) {
            List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<EdgeAssociation> ref : edgeAssociations) {
                EdgeAssociation<?> as = ref.get();
                if (as == null)
                    toDelete.add(ref); // reference is dead
                else {
                    as.put(edge, null);
                }
            }
            for (WeakReference ref : toDelete) {
                edgeAssociations.remove(ref);
            }
        }
    }

    /**
     * called from constructor of EdgeSet to register with graph
     *
     * @param set
     */
    void registerEdgeSet(EdgeSet set) {
        synchronized (edgeSets) {
            final List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<EdgeSet> ref : edgeSets) {
                if (ref.get() == null)
                    toDelete.add(ref); // reference is dead
            }
            if (toDelete.size() > 0)
                edgeSets.removeAll(toDelete);
            edgeSets.add(new WeakReference<>(set));
        }
    }

    /**
     * called from deleteEdge to clean all array entries for the edge
     *
     * @param e
     */
    void deleteEdgeFromSets(Edge e) {
        checkOwner(e);
        synchronized (edgeSets) {
            List<WeakReference> toDelete = new LinkedList<>();
            for (WeakReference<EdgeSet> ref : edgeSets) {
                EdgeSet set = ref.get();
                if (set == null)
                    toDelete.add(ref); // reference is dead
                else {
                    set.remove(e);
                }
            }
            for (WeakReference ref : toDelete) {
                edgeSets.remove(ref);
            }
        }
    }

    /**
     * gets the number of connected components of the graph
     *
     * @return connected components
     */
    public int getNumberConnectedComponents() {
        int result = 0;
        NodeSet used = new NodeSet(this);

        for (Node v = getFirstNode(); v != null; v = v.getNext()) {
            if (!used.contains(v)) {
                visitConnectedComponent(v, used);
                result++;
            }
        }
        return result;
    }

    /**
     * visit all nodes in a connected component
     *
     * @param v
     * @param used
     */
    public void visitConnectedComponent(Node v, NodeSet used) {
        used.add(v);
        for (Edge f = getFirstAdjacentEdge(v); f != null; f = v.getNextAdjacentEdge(f)) {
            Node w = f.getOpposite(v);
            if (!used.contains(w))
                visitConnectedComponent(w, used);
        }
    }

    /**
     * gets the current maximal node id
     *
     * @return max node id
     */
    int getMaxNodeId() {
        return idsNodes;
    }

    /**
     * gets the current maximal edge id
     *
     * @return max edge id
     */
    int getMaxEdgeId() {
        return idsEdges;
    }

    /**
     * is this a special edge?
     *
     * @param e
     * @return true, if marked as special
     */
    public boolean isSpecial(Edge e) {
        return specialEdges.size() > 0 && specialEdges.contains(e);
    }

    /**
     * mark as special or not
     *
     * @param e
     * @param special
     */
    public void setSpecial(Edge e, boolean special) {
        if (special && !specialEdges.contains(e))
            specialEdges.add(e);
        else if (!special)
            specialEdges.remove(e);
    }

    /**
     * gets the number of special edges
     *
     * @return number of special edges
     */
    public int getNumberSpecialEdges() {
        return specialEdges.size();
    }

    /**
     * gets the set of special edges
     *
     * @return special edges
     */
    public EdgeSet getSpecialEdges() {
        return specialEdges;
    }

    /**
     * get the non-special-edge connected component containing v
     *
     * @param v
     * @return component
     */
    public NodeSet getSpecialComponent(Node v) {
        NodeSet nodes = new NodeSet(this);
        List<Node> queue = new LinkedList<>();
        queue.add(v);
        nodes.add(v);
        while (queue.size() > 0) {
            v = queue.remove(0);
            for (Edge e = v.getFirstAdjacentEdge(); e != null; e = v.getNextAdjacentEdge(e)) {
                if (!isSpecial(e)) {
                    Node w = e.getOpposite(v);
                    if (!nodes.contains(w)) {
                        queue.add(w);
                        nodes.add(w);
                    }
                }
            }
        }
        return nodes;
    }

    /**
     * erase all data components
     */
    public void clearData() {
        for (Node v = getFirstNode(); v != null; v = v.getNext()) {
            v.setData(null);
        }
    }

    /*public NodeSet computeSetOfLeaves(Node v) {
        NodeSet sons = new NodeSet(this);
        getLeavesRec(v,sons);
        return sons;
   }
    public void getLeavesRec(Node v, NodeSet nodes) {
        for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
           Node w = f.getTarget();
           getLeavesRec(w,sons);
       }
                  if (v.getOutDegree() == 0)
           nodes.add(w);
        return sons;
   }*/

    /**
     * gets all nodes as a new set
     *
     * @return node set of nodes
     */
    public NodeSet getNodesAsSet() {
        NodeSet nodes = new NodeSet(this);
        for (Node v : nodes())
            nodes.add(v);
        return nodes;
    }

    /**
     * iterable over all edges
     *
     * @return iterable over all edges
     */
    public Iterable<Edge> edges() {
        return edges(null);
    }

    /**
     * iterable over all edges after given edge prev
     *
     * @param afterMe the previous edge, after which the iteration starts. If null, iterates over all edges
     * @return iterable over all edges
     */
    public Iterable<Edge> edges(Edge afterMe) {
        return () -> new Iterator<>() {
            Edge e = (afterMe == null ? getFirstEdge() : afterMe.getNext());

            @Override
            public boolean hasNext() {
                return e != null;
            }

            @Override
            public Edge next() {
                Edge result = e;
                e = e.getNext();
                return result;
            }
        };
    }

    /**
     * gets all edges as a new set
     *
     * @return edge set of edges
     */
    public EdgeSet getEdgesAsSet() {
        EdgeSet edges = new EdgeSet(this);
        for (Edge e : edges())
            edges.add(e);
        return edges;
    }

    /**
     * iterable over all nodes
     *
     * @return iterable over all nodes
     */
    public Iterable<Node> nodes() {
        return nodes(null);
    }

    /**
     * iterable over all nodes after given node prev
     *
     * @param afterMe the previous node, after which the iteration starts. If null, iterates over all nodes
     * @return iterable over all nodes
     */
    public Iterable<Node> nodes(Node afterMe) {
        return () -> new Iterator<>() {
            Node v = (afterMe == null ? getFirstNode() : afterMe.getNext());

            @Override
            public boolean hasNext() {
                return v != null;
            }

            @Override
            public Node next() {
                Node result = v;
                v = v.getNext();
                return result;
            }
        };
    }

    /**
     * get the unhidden subset
     *
     * @param nodes
     * @return
     */
    public NodeSet getUnhiddenSubset(Collection<Node> nodes) {
        NodeSet unhidden = new NodeSet(this);
        for (Node v : nodes) {
            if (!v.isHidden())
                unhidden.add(v);
        }
        return unhidden;
    }

    /**
     * reorders nodes in graph. These nodes are put at the front of the list of nodes
     *
     * @param nodes
     */
    public void reorderNodes(List<Node> nodes) {
        final List<Node> newOrder = new ArrayList<>(numberNodes + numberOfNodesThatAreHidden);
        final Set<Node> toMove = new HashSet<>();
        for (Node v : nodes) {
            if (v.getOwner() != null) {
                newOrder.add(v);
                toMove.add(v);
            }
        }

        if (toMove.size() > 0) {
            for (Iterator<Node> it = nodeIteratorIncludingHidden(); it.hasNext(); ) {
                Node v = it.next();
                if (!toMove.contains(v))
                    newOrder.add(v);
            }

            Node previousNode = null;
            for (Node v : newOrder) {
                if (previousNode == null) {
                    firstNode = v;
                    v.prev = null;
                } else {
                    previousNode.next = v;
                    v.prev = previousNode;
                }
                previousNode = v;
            }
            if (previousNode != null) {
                previousNode.next = null;
            }
            lastNode = previousNode;
        }
    }

    /**
     * write a graph in GML
     *
     * @param w
     * @param comment
     * @param directed
     * @param label
     * @param graphId
     * @throws IOException
     */
    public void writeGML(Writer w, String comment, boolean directed, String label, int graphId) throws IOException {
        writeGML(w, comment, directed, label, graphId, null, null);
    }


    /**
     * write a graph in GML
     *
     * @param w
     * @param comment
     * @param directed
     * @param label
     * @param graphId
     * @throws IOException
     */
    public void writeGML(Writer w, String comment, boolean directed, String label, int graphId, Map<String, NodeArray<?>> label2nodes, Map<String, EdgeArray<?>> label2edges) throws IOException {
        w.write("graph [\n");
        if (comment != null)
            w.write("\tcomment \"" + comment + "\"\n");
        w.write("\tdirected " + (directed ? 1 : 0) + "\n");
        w.write("\tid " + graphId + "\n");
        if (label != null)
            w.write("\tlabel \"" + label + "\"\n");
        boolean hasNodeLabels = (label2nodes != null && label2nodes.containsKey("label"));
        for (Node v = getFirstNode(); v != null; v = getNextNode(v)) {
            w.write("\tnode [\n");
            w.write("\t\tid " + v.getId() + "\n");
            if(label2nodes!=null) {
                for (String aLabel : label2nodes.keySet()) {
                    NodeArray<?> array = label2nodes.get(aLabel);
                    if (array != null) {
                        Object obj = array.get(v);
                        w.write("\t\t" + aLabel + " \"" + (obj != null ? obj.toString() : "null") + "\"\n");
                    }
                }
            }
            if (!hasNodeLabels)
                w.write("\t\tlabel \"" + (v.getInfo() != null ? v.getInfo().toString() : "null") + "\"\n");

            w.write("\t]\n");
        }
        boolean hasEdgeLabels = (label2edges != null && label2edges.containsKey("label"));

        for (Edge e = getFirstEdge(); e != null; e = getNextEdge(e)) {
            w.write("\tedge [\n");
            w.write("\t\tsource " + e.getSource().getId() + "\n");
            w.write("\t\ttarget " + e.getTarget().getId() + "\n");

            if(label2edges!=null) {
                for (String aLabel : label2edges.keySet()) {
                    EdgeArray<?> array = label2edges.get(aLabel);
                    if (array != null) {
                        Object obj = array.get(e);
                        w.write("\t\t" + aLabel + " \"" + (obj != null ? obj.toString() : "null") + "\"\n");
                    }
                }
            }
            if (!hasEdgeLabels)
                w.write("\t\tlabel \"" + (e.getInfo() != null ? e.getInfo().toString() : "null") + "\"\n");

            w.write("\t]\n");
        }
        w.write("]\n");
        w.flush();
    }

    /**
     * read a graph in GML for that was previously saved using writeGML. This is not a general parser.
     *
     * @param r
     */
    public void readGML(Reader r) throws IOException {
        final NexusStreamParser np = new NexusStreamParser(r);
        np.setSquareBracketsSurroundComments(false);

        clear();

        np.matchIgnoreCase("graph [");
        if (np.peekMatchIgnoreCase("comment")) {
            np.matchIgnoreCase("comment");
            System.err.println("Comment: " + getQuotedString(np));
        }
        np.matchIgnoreCase("directed");
        System.err.println("directed: " + (np.getInt(0, 1) == 1));
        np.matchIgnoreCase("id");
        System.err.println("id: " + np.getInt());
        if (np.peekMatchIgnoreCase("label")) {
            np.matchIgnoreCase("label");
            System.err.println("Label: " + getQuotedString(np));
        }


        Map<Integer, Node> id2node = new HashMap<>();
        while (np.peekMatchIgnoreCase("node")) {
            np.matchIgnoreCase("node [");
            np.matchIgnoreCase("id");
            int id = np.getInt();
            Node v = newNode();
            id2node.put(id, v);
            if (np.peekMatchIgnoreCase("label")) {
                np.matchIgnoreCase("label");
                v.setInfo(getQuotedString(np));
            }
            np.matchIgnoreCase("]");
        }
        while (np.peekMatchIgnoreCase("edge")) {
            np.matchIgnoreCase("edge [");
            np.matchIgnoreCase("source");
            int sourceId = np.getInt();
            np.matchIgnoreCase("target");
            int targetId = np.getInt();
            if (!id2node.containsKey(sourceId))
                throw new IOException("Undefined node id: " + sourceId);
            if (!id2node.containsKey(targetId))
                throw new IOException("Undefined node id: " + targetId);
            Edge e = newEdge(id2node.get(sourceId), id2node.get(targetId));
            if (np.peekMatchIgnoreCase("label")) {
                np.matchIgnoreCase("label");
                e.setInfo(getQuotedString(np));
            }
            np.matchIgnoreCase("]");
        }
        np.matchIgnoreCase("]");
    }

    public static String getQuotedString(NexusStreamParser np) throws IOException {
        np.matchIgnoreCase("\"");
        ArrayList<String> words = new ArrayList<>();
        while (!np.peekMatchIgnoreCase("\""))
            words.add(np.getWordRespectCase());
        return Basic.toString(words, " ");
    }

    /**
     * compute the current set of all leaves
     *
     * @return all leaves
     */
    public NodeSet computeSetOfLeaves() {
        NodeSet nodes = new NodeSet(this);
        for (Node v = getFirstNode(); v != null; v = getNextNode(v))
            if (v.getOutDegree() == 0)
                nodes.add(v);
        return nodes;
    }

    /**
     * compute the max id of any node
     *
     * @return max id
     */
    public int computeMaxId() {
        int max = 0;
        for (Node v = getFirstNode(); v != null; v = getNextNode(v)) {
            if (max < v.getId())
                max = v.getId();
        }
        return max;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets an enumeration of all node labels.
     */
    public Set<String> getNodeLabels() {
        Set<String> set = new HashSet<>();

        for (Node v = getFirstNode(); v != null; v = getNextNode(v))
            if (getLabel(v) != null && getLabel(v).length() > 0)
                set.add(getLabel(v));

        return set;
    }
}

// EOF
