package edu.sjsu.cs249.chain.impl;

import edu.sjsu.cs249.chain.*;
import edu.sjsu.cs249.chain.utils.ChainUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Replica extends ReplicaGrpc.ReplicaImplBase {

    @Override
    public void ack(AckRequest request, StreamObserver<AckResponse> responseObserver) {
        System.out.println("Received ack request for xid: " + request.getXid());

        //Remove received update request from sent list
        Optional<UpdateRequest> receivedUpdateReq = ChainReplicaMgr.sentList.stream().filter(req -> req.getXid() == request.getXid()).findFirst();
        if (!receivedUpdateReq.isEmpty()) {
            ChainReplicaMgr.sentList.remove(receivedUpdateReq);
        }

        //notify head when I am head: ack for corresponding xid is received
        if (ChainReplicaMgr.isHead && ChainReplicaMgr.waitObjMap.containsKey(request.getXid())) {
            System.out.println("In ack now notify head");
            synchronized (ChainReplicaMgr.waitObjMap.get(request.getXid())) {
                ChainReplicaMgr.waitObjMap.get(request.getXid()).notify();
            }
        }

        //I am middle node, send ack to predecessor
        if(!ChainReplicaMgr.isHead && !ChainReplicaMgr.isTail && ChainReplicaMgr.xid == request.getXid()) {
            System.out.println("Sending ack request");
            ManagedChannel channel = ManagedChannelBuilder.forTarget(ChainReplicaMgr.predecessorIp).usePlaintext().build();
            ReplicaGrpc.ReplicaBlockingStub replicaStub = ReplicaGrpc.newBlockingStub(channel);
            System.out.println("sending ack with xid" + ChainReplicaMgr.xid);
            AckRequest ackRequest = AckRequest.newBuilder().setXid(ChainReplicaMgr.xid).build();
            AckResponse ackResponse = replicaStub.ack(ackRequest);
            channel.shutdown();
            System.out.println("Got ack response");
        }

        //send ack response
        AckResponse.Builder response = AckResponse.newBuilder();
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public synchronized void update(UpdateRequest request, StreamObserver<UpdateResponse> responseObserver) {
        System.out.println("Update request received ");
        System.out.println("request xid: " + request.getXid());
        System.out.println("my xid: " + ChainReplicaMgr.xid);
        //I am tail
        if (ChainReplicaMgr.isTail && request.getXid() - ChainReplicaMgr.xid > 0) {
            System.out.println("In update: i am tail");
            int value = request.getNewValue();
            //1. Update metadata
            ChainUtils.setData(true, request.getKey(), value, null);

            //2. Send update response
            UpdateResponse.Builder response = UpdateResponse.newBuilder();
            response.setRc(0);                      //respond with updated user list
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
            System.out.println("Update request sent ");


            //3. Send ack immediately as I am tail
            System.out.println("Sending acknowledgement");
            ManagedChannel channel = ManagedChannelBuilder.forTarget(ChainReplicaMgr.predecessorIp).usePlaintext().build();
            ReplicaGrpc.ReplicaBlockingStub stub = ReplicaGrpc.newBlockingStub(channel);
            AckRequest ackRequest = AckRequest.newBuilder().setXid(ChainReplicaMgr.xid).build();
            System.out.println("sending acknowledgement with xid" + ChainReplicaMgr.xid);
            AckResponse ackResponse = stub.ack(ackRequest);
            System.out.println("acknowledgement sent");
            channel.shutdown();

        }
        //I am middle node
        else if (request.getXid() - ChainReplicaMgr.xid > 0) {
            System.out.println("In update: i am middle node");
            int value = request.getNewValue();
            //1. Update all data
            ChainUtils.setData(true, request.getKey(), value, request);

            //2. Send update response
            UpdateResponse.Builder resp = UpdateResponse.newBuilder();
            resp.setRc(0);
            responseObserver.onNext(resp.build());
            responseObserver.onCompleted();
            System.out.println(ChainReplicaMgr.mapData.toString());
            System.out.println("Sent update response");

            //3. Send update request to successor
            System.out.println("Sending update request");
            ManagedChannel channel = ManagedChannelBuilder.forTarget(ChainReplicaMgr.successorIp).usePlaintext().build();
            ReplicaGrpc.ReplicaBlockingStub stub = ReplicaGrpc.newBlockingStub(channel);
            UpdateRequest updateRequest = UpdateRequest.newBuilder().setKey(request.getKey()).setNewValue(value).setXid(ChainReplicaMgr.xid).build();
            UpdateResponse updateResponse = stub.update(updateRequest);
            System.out.println("Got update response");

            //To fix
            if (updateResponse.getRc() == 0) {
                System.out.println("Sending ack request");
                ManagedChannel channelPred = ManagedChannelBuilder.forTarget(ChainReplicaMgr.predecessorIp).usePlaintext().build();
                ReplicaGrpc.ReplicaBlockingStub replicaStubPred = ReplicaGrpc.newBlockingStub(channelPred);
                System.out.println("sending ack with xid" + ChainReplicaMgr.xid);
                AckRequest ackRequest = AckRequest.newBuilder().setXid(ChainReplicaMgr.xid).build();
                AckResponse ackResponse = replicaStubPred.ack(ackRequest);
                channelPred.shutdown();
                System.out.println("Got ack response");
            }
        }
        // Bad xid received, something's wrong, leave
        else if (request.getXid() - ChainReplicaMgr.xid < 0) {
            System.out.println("In update Request leave condn myXID: " + ChainReplicaMgr.xid);
            System.out.println("In update Request leave condn reqXID: " + request.getXid());
            System.out.println("Something is wrong. Leave!!!!");
            System.exit(0);
        }
    }

    @Override
    public synchronized void stateTransfer(StateTransferRequest request, StreamObserver<StateTransferResponse> responseObserver) {
        System.out.println("Received state transfer request");

        //1. Check xid is correct
        if (request.getXid() - ChainReplicaMgr.xid > 0) {
            System.out.println("Received sentlist: " + request.getSentList());
            System.out.println("Received xid: " + request.getXid());

            //2. Update all my data
            ChainUtils.setDirectMetaData(request.getXid(), new ConcurrentHashMap<String, Integer>(request.getStateMap()), request.getSentList());
            List<UpdateRequest> predecessorSentList = request.getSentList();

            //3. Send acks for all pending requests
            for (UpdateRequest updateRequest : predecessorSentList) {
                ManagedChannel channel = ManagedChannelBuilder.forTarget(ChainReplicaMgr.predecessorIp).usePlaintext().build();
                ReplicaGrpc.ReplicaBlockingStub replicaAck = ReplicaGrpc.newBlockingStub(channel);
                AckRequest ackRequest = AckRequest.newBuilder().setXid(updateRequest.getXid()).build();
                replicaAck.ack(ackRequest);
                channel.shutdown();
            }
        } else {
            // Error
            StateTransferResponse response = StateTransferResponse.newBuilder().setRc(1).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        //send success response
        StateTransferResponse response = StateTransferResponse.newBuilder().setRc(0).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
