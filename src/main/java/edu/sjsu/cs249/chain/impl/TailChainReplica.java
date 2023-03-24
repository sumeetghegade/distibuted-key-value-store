package edu.sjsu.cs249.chain.impl;

import edu.sjsu.cs249.chain.GetRequest;
import edu.sjsu.cs249.chain.GetResponse;
import edu.sjsu.cs249.chain.TailChainReplicaGrpc;
import io.grpc.stub.StreamObserver;

public class TailChainReplica extends TailChainReplicaGrpc.TailChainReplicaImplBase {

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        System.out.println("I am Tail... : entered get");
        //I am tail
        if (ChainReplicaMgr.isTail) {
            int value = 0;
            //Return value if I have key
            if(ChainReplicaMgr.mapData.containsKey(request.getKey())) {
                value = ChainReplicaMgr.mapData.get(request.getKey());
                GetResponse.Builder response = GetResponse.newBuilder();
                response.setRc(0);
                response.setValue(value);
                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
            }
            //Return value = 0 if I don't have key
            else
            {
                value = 0;
                GetResponse.Builder resp = GetResponse.newBuilder();
                resp.setRc(0);
                resp.setValue(value);
                responseObserver.onNext(resp.build());
                responseObserver.onCompleted();
            }

        }
        //I should not be here if I am not tail
        else {
            GetResponse.Builder tailResp = GetResponse.newBuilder();
            tailResp.setRc(1);
            responseObserver.onNext(tailResp.build());
            responseObserver.onCompleted();
            return;
        }


    }
}
