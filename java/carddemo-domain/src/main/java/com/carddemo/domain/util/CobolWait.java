package com.carddemo.domain.util;
/** MVS ASMWAIT-compatible wait; no-op by default because batch sequencing replaces JCL waits. */
public final class CobolWait {
  private CobolWait(){}
  public static void waitCentiseconds(long value){waitCentiseconds(value,false);}
  public static void waitCentiseconds(long value,boolean realSleep){
    if(!realSleep||value<=0)return;
    try{Thread.sleep(value*10L);}catch(InterruptedException e){Thread.currentThread().interrupt();}
  }
}
