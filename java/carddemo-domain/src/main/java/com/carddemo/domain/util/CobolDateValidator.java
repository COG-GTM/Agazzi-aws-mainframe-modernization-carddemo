package com.carddemo.domain.util;
import java.time.LocalDate;
import java.time.format.*;
/** CEEDAYS-style validator; internal LE era/range diagnostics are not reproducible exactly. */
public final class CobolDateValidator {
  private CobolDateValidator(){}
  public record Result(int severity,int messageNumber,String resultText,String message){}
  public static Result validate(String date,String mask){
    String d=date==null?"":date,m=mask==null?"":mask; int sev=0,code=0; String result="Date is valid";
    if(d.isBlank()){sev=3;code=1;result="Insufficient";}
    else if(!"YYYY-MM-DD".equals(m)){sev=3;code=7;result="Bad Pic String ";}
    else if(!d.matches("\\d{4}-\\d{2}-\\d{2}")){sev=3;code=8;result="Nonnumeric data";}
    else try{LocalDate.parse(d,DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT));}
    catch(DateTimeParseException e){int month=Integer.parseInt(d.substring(5,7));sev=3;code=6;result=month<1||month>12?"Invalid month  ":"Date is invalid";}
    String msg=String.format("%04dMesg Code:%04d %-15s TstDate:%-10s Mask used:%-10s   ",sev,code,result,d,m);
    msg=msg.substring(0,Math.min(80,msg.length())); return new Result(sev,code,result,msg+" ".repeat(80-msg.length()));
  }
}
