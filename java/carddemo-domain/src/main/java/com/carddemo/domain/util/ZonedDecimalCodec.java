package com.carddemo.domain.util;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class ZonedDecimalCodec {
  private ZonedDecimalCodec() {}
  public static BigDecimal decode(String value) {
    if (value == null || value.isBlank()) return BigDecimal.ZERO.setScale(2);
    String s=value.trim(); int sign=1;
    if(s.startsWith("+")||s.startsWith("-")){if(s.charAt(0)=='-')sign=-1;s=s.substring(1);}
    char last=s.charAt(s.length()-1);
    if(last=='{')last='0'; else if(last=='}'){last='0';sign=-1;}
    else if(last>='A'&&last<='I')last=(char)('0'+last-'A'+1);
    else if(last>='J'&&last<='R'){last=(char)('0'+last-'J'+1);sign=-1;}
    if(!Character.isDigit(last))throw new IllegalArgumentException("Invalid zoned decimal: "+value);
    String digits=s.substring(0,s.length()-1)+last;
    if(!digits.chars().allMatch(Character::isDigit))throw new IllegalArgumentException("Invalid zoned decimal: "+value);
    return BigDecimal.valueOf(sign).multiply(new BigDecimal(digits)).movePointLeft(2).setScale(2);
  }
  public static String encode(BigDecimal number,int length){
    if(number==null)number=BigDecimal.ZERO;
    String d=number.setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).abs().toBigIntegerExact().toString();
    if(d.length()>length)throw new IllegalArgumentException("Value does not fit "+length);
    d="0".repeat(length-d.length())+d; int i=d.length()-1,n=d.charAt(i)-'0';
    char over=number.signum()<0?(n==0?'}':(char)('J'+n-1)):(n==0?'{':(char)('A'+n-1));
    return d.substring(0,i)+over;
  }
}
