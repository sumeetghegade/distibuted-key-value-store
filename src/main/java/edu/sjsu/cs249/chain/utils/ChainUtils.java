package edu.sjsu.cs249.chain.utils;

import edu.sjsu.cs249.chain.ReplicaGrpc;
import edu.sjsu.cs249.chain.StateTransferRequest;
import edu.sjsu.cs249.chain.StateTransferResponse;
import edu.sjsu.cs249.chain.UpdateRequest;
import edu.sjsu.cs249.chain.impl.ChainReplicaMgr;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChainUtils {

    //set xid, data and sentList synchronously
    //only this method is used to change this data
    public static synchronized void setData(boolean incXid, String key, int value, UpdateRequest request) {
        if (incXid) {
            ChainReplicaMgr.xid = ChainReplicaMgr.xid + 1;
            System.out.println(ChainReplicaMgr.xid);
        }
        if (key != null) {
            ChainReplicaMgr.mapData.put(key, value);
        }
        if (request != null) {
            ChainReplicaMgr.sentList.add(request);
        }

    }

    //set xid, data and sentList synchronously
    public static synchronized void setDirectMetaData(int xid, ConcurrentHashMap map, List<UpdateRequest> requestList) {

        ChainReplicaMgr.xid = xid;
        if (map != null) {
            ChainReplicaMgr.mapData = map;
        }
        if (requestList != null) {
            ChainReplicaMgr.sentList.addAll(requestList);
        }
    }

    //send state transfer request
    public static void sendStateTransferRequest(String newSuccesorIP) {
        System.out.println("send StateTransferRequest");
        System.out.println("sending: " + ChainReplicaMgr.sentList.toString());
        System.out.println(ChainReplicaMgr.sentList.toString());
        ChainReplicaMgr.successorIp = newSuccesorIP;

        ManagedChannel channel = ManagedChannelBuilder.forTarget(newSuccesorIP).usePlaintext().build();
        ReplicaGrpc.ReplicaBlockingStub repStub = ReplicaGrpc.newBlockingStub(channel);
        StateTransferRequest req = StateTransferRequest.newBuilder().addAllSent(ChainReplicaMgr.sentList).setXid(ChainReplicaMgr.xid).putAllState(ChainReplicaMgr.mapData).build();
        StateTransferResponse resp = repStub.stateTransfer(req);
        channel.shutdown();
        System.out.println("Exit sendStateTransferRequest");
    }

}
