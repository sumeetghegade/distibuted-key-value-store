package edu.sjsu.cs249.chain.impl;

import edu.sjsu.cs249.chain.*;
import edu.sjsu.cs249.chain.utils.ChainUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class HeadChainReplica extends HeadChainReplicaGrpc.HeadChainReplicaImplBase {

    @Override
    public synchronized void increment(IncRequest request, StreamObserver<HeadResponse> responseObserver) {


        if (!ChainReplicaMgr.isHead) {
            // I should not be here if I am not head
            HeadResponse.Builder headResponse = HeadResponse.newBuilder();
            headResponse.setRc(1);

            responseObserver.onNext(headResponse.build());
            responseObserver.onCompleted();
            return;
        } else {
            //I am alone in the chain
            if(ChainReplicaMgr.isHead && ChainReplicaMgr.isTail) {
                String requestKey = request.getKey();
                int value = ChainReplicaMgr.mapData.getOrDefault(request.getKey(), 0);
                int newValue = value + request.getIncValue();

                //Update my data
                UpdateRequest req = UpdateRequest.newBuilder().setNewValue(newValue).setXid(ChainReplicaMgr.xid + 1).setKey(requestKey).build();
                ChainUtils.setData(true, requestKey, newValue, null);

                HeadResponse.Builder headResponse = HeadResponse.newBuilder();
                headResponse.setRc(0);

                responseObserver.onNext(headResponse.build());
                responseObserver.onCompleted();
            } else {
                String requestKey = request.getKey();
                int value = ChainReplicaMgr.mapData.getOrDefault(request.getKey(), 0);
                int newValue = value + request.getIncValue();
                //1. Update my data
                UpdateRequest req = UpdateRequest.newBuilder().setNewValue(newValue).setXid(ChainReplicaMgr.xid + 1).setKey(requestKey).build();
                ChainUtils.setData(true, requestKey, newValue, req);

                //Send update response
                ManagedChannel channel = ManagedChannelBuilder.forTarget(ChainReplicaMgr.successorIp).usePlaintext().build();
                ReplicaGrpc.ReplicaBlockingStub stub = ReplicaGrpc.newBlockingStub(channel);
                UpdateRequest updateRequest = UpdateRequest.newBuilder().setKey(requestKey).setNewValue(newValue).setXid(ChainReplicaMgr.xid).build();
                UpdateResponse updateResponse = stub.update(updateRequest);

                //This works
                //Following sync block will wait till an ack for corresponding XID is received.
                //Create a map where key is xid and value is a random object
                Object tempObj = new Object();
                ChainReplicaMgr.waitObjMap.put(ChainReplicaMgr.xid, tempObj);
                synchronized (tempObj) {
                    try {
                        System.out.println("waiting in head for ack");
                        tempObj.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                //Received ack now respond
                HeadResponse.Builder resp = HeadResponse.newBuilder();
                resp.setRc(0);
                responseObserver.onNext(resp.build());
                responseObserver.onCompleted();

            }
        }
    }
}
