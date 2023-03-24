package edu.sjsu.cs249.chain.impl;

import edu.sjsu.cs249.chain.UpdateRequest;
import edu.sjsu.cs249.chain.utils.ChainUtils;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class ChainReplicaMgr {

    public static final int PORT = 9097;

    //data
    public static ConcurrentHashMap<String, Integer> mapData = new ConcurrentHashMap<String, Integer>();

    //metadata
    public static boolean isHead = false, isTail = false;
    public static String successorIp = null;
    public static String predecessorIp = null;
    public static String ip = null;
    public static ConcurrentLinkedQueue<UpdateRequest> sentList = new ConcurrentLinkedQueue<UpdateRequest>();
    public static int xid = 0;

    //Use this map to wait for ack when head
    public static ConcurrentHashMap<Integer, Object> waitObjMap = new ConcurrentHashMap<Integer, Object>();

    // create static instance for ZooKeeperConnection class.
    private static ZooKeeper zk;
    private static ZooKeeperConnection conn;


    // Method to create znode in zookeeper ensemble
    public static String create(String path, byte[] data) throws
            KeeperException, InterruptedException {
        String myNode = zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        return myNode;
    }


    //Watcher to update and transfer metadata
    public static void updateAndTransferMetaData(ZooKeeper zookeeper, String controlPath, String myNodeName) {
        try {
            Watcher workersChangeWatcher = new Watcher() {
            @Override
            public void process(WatchedEvent e) {
                if (e.getType() == Event.EventType.NodeChildrenChanged) {
                    updateAndTransferMetaData(zookeeper, controlPath, myNodeName);
                }
            }
        };

            System.out.println("************  Something changed  *********************");
            List<String> nodeList = zookeeper.getChildren(controlPath, workersChangeWatcher);
            Collections.sort(nodeList);

            System.out.println("nodeList:  " + nodeList.toString());
            System.out.println("myNodeName:  " + myNodeName);
            //updateMetaData
            //Case1: I am the only node in the chain
            if (nodeList.size() == 1) {
                System.out.println("I am the only node in the chain");
                isHead = true;
                isTail = true;
                predecessorIp = null;
                successorIp = null;
                System.out.println("I am the only node in the chain");
            }
            //Case2: I am the head
            else if (myNodeName.equals(nodeList.get(0))) {
                System.out.println("I am the head");
                isHead = true;
                isTail = false;
                predecessorIp = null;

                //Send state transfer request if successor changes
                if (successorIp != new String(zookeeper.getData(controlPath + "/" + nodeList.get(1), false, null), StandardCharsets.UTF_8).split("\n", 2)[0])
                    ChainUtils.sendStateTransferRequest(new String(zookeeper.getData(controlPath + "/" + nodeList.get(1), false, null), StandardCharsets.UTF_8).split("\n", 2)[0]);
                System.out.println("I am the head");
            }
            //Case3: I am the tail
            else if (myNodeName.equals(nodeList.get(nodeList.size() - 1))) {
                System.out.println("I am the tail");
                isTail = true;
                isHead = false;
                successorIp = null;
                predecessorIp = new String(zookeeper.getData(controlPath + "/" + nodeList.get(nodeList.size() - 2), false, null), StandardCharsets.UTF_8).split("\n", 2)[0];
                System.out.println("I am the tail");
            }
            //Case4: I am a middle node
            else {
                System.out.println("I am a middle node");
                isTail = false;
                isHead = false;
                //Send state transfer request if successor changes
                if (successorIp != new String(zookeeper.getData(controlPath + "/" + nodeList.get(nodeList.indexOf(myNodeName) + 1), false, null), StandardCharsets.UTF_8).split("\n", 2)[0])
                    ChainUtils.sendStateTransferRequest(new String(zookeeper.getData(controlPath + "/" + nodeList.get(nodeList.indexOf(myNodeName) + 1), false, null), StandardCharsets.UTF_8).split("\n", 2)[0]);
                predecessorIp = new String(zookeeper.getData(controlPath + "/" + nodeList.get(nodeList.indexOf(myNodeName) - 1), false, null), StandardCharsets.UTF_8).split("\n", 2)[0];
                System.out.println("I am a middle node");
            }

            System.out.println("isTail: " + isTail);
            System.out.println("isHead: " + isHead);
            System.out.println("successorIp: " + successorIp);
            System.out.println("predecessorIp: " + predecessorIp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {

        String zkHostPort = args[0];
        String controlPath = args[1];

        /* String zkHostPort = "localhost";            //for local testing
         String controlPath = "/MyParentNode/replica-"; // Assign path to znode */

        //Get my IP address
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            ip = inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        //Set data for my node
        byte[] data = new byte[0]; // Declare data
        data = ("172.24.9.118" + ":" + PORT + "\n" + "Sumeet Ghegade").getBytes(StandardCharsets.UTF_8);


        // Connect with zooKeeper
        conn = new ZooKeeperConnection();
        try {
            zk = conn.connect(zkHostPort);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create the node with data to the specified controlPath
        String myNodePath = null;
        try {
            myNodePath = create(controlPath + "/replica-", data);
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Get my node name
        String[] nodePathList = myNodePath.split("/");
        String myNodeName = nodePathList[nodePathList.length - 1].trim();
        System.out.println("Created my node:   " + myNodeName);

        //start watcher on chain
        updateAndTransferMetaData(zk, controlPath, myNodeName);

        //start GRPC server
        Server myServer = ServerBuilder.forPort(PORT).addService(new Replica()).addService(new HeadChainReplica()).addService(new TailChainReplica()).build();
        try {
            myServer.start();
            myServer.awaitTermination();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public static class ZooKeeperConnection {
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        // declare zookeeper instance to access ZooKeeper ensemble
        private ZooKeeper zoo;

        // Method to connect zookeeper ensemble.
        public ZooKeeper connect(String host) throws IOException, InterruptedException {
            zoo = new ZooKeeper(host, 5000, new Watcher() {
                public void process(WatchedEvent we) {
                    if (we.getState() == Event.KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            connectedSignal.await();
            return zoo;
        }

        // Method to disconnect from zookeeper server
        public void close() throws InterruptedException {
            zoo.close();
        }

    }
}