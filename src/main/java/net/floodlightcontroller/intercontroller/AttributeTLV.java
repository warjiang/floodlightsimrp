package net.floodlightcontroller.intercontroller;

public class AttributeTLV {
	public Integer type;
	public Integer length;
	public int value;
	
	public AttributeTLV(){
		this.type = 0;
		this.length = 0;
		this.value = 0;
	}
	
	public Integer getType(){
		return this.type;
	}
	public Integer getLen(){
		return this.length;
	}
	public int getValue(){
		return this.value;
	}
	
	public AttributeTLV(Integer type, Integer len, int value){
		this.type   = type;
		this.length = len;
		this.value  = value;
	}
	
	public static byte[] attributeTLV2ByteArray(AttributeTLV attr){
		byte[] bAttr = new byte[8];
		byte[] tmp1  = new byte[2];
		byte[] tmp2  = new byte[4];
		tmp1 = EncodeData.Integer2ByteArray(attr.type);
		for(int i=0;i<2;i++)
			bAttr[i] = tmp1[i];
		tmp1 = EncodeData.Integer2ByteArray(attr.length);
		for(int i=0;i<2;i++)
			bAttr[2+i] = tmp1[i];
		tmp2 = EncodeData.Int2ByteArray(attr.value);
		for(int i=0;i<4;i++)
			bAttr[4+i] = tmp2[i];
		return bAttr;
	}

}
