// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: chain.proto

package edu.sjsu.cs249.chain;

public interface UpdateResponseOrBuilder extends
    // @@protoc_insertion_point(interface_extends:chain.UpdateResponse)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * rc = 0 means success, rc = 1 i'm a new tail (i need a state transfer)
   * rc = 1 should only happen on the first update to a new sucessor
   * </pre>
   *
   * <code>uint32 rc = 1;</code>
   */
  int getRc();
}