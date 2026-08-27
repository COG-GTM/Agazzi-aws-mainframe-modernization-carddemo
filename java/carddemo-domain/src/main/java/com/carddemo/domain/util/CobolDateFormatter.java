package com.carddemo.domain.util;
/** Reimplementation of COBDATFT.asm's YYYYMMDD and YYYY-MM-DD conversion. */
public final class CobolDateFormatter {
  private CobolDateFormatter(){}
  public record Result(String value,String error){}
  public static Result format(String input,String inputType,String outputType){
    if(input==null)return new Result("","INVALID INPUT");
    if("1".equals(inputType)&&"2".equals(outputType)&&input.length()==8&&!input.contains("-"))return new Result(input.substring(0,4)+"-"+input.substring(4,6)+"-"+input.substring(6),"");
    if("2".equals(inputType)&&"1".equals(outputType)&&input.length()==10)return new Result(input.substring(0,4)+input.substring(5,7)+input.substring(8),"");
    return new Result("","INVALID INPUT");
  }
}
