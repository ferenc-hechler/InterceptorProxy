package de.hechler.interceptor.https.modify;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {

	public static String now() {
		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
		return fmt.format(new Date());
	}
	
	public static String round(double d, int decimals) {
		return String.format("%."+decimals+"f", d);
	}

	public static String prettyBytes(long bytes) {
		if (bytes < 1024) {
			return bytes+"b";
		}
		if (bytes < 1024*1024) {
			return round(bytes/1024,1)+"kb";
		}
		if (bytes < 1024*1024*1024) {
			return round(bytes/1024/1024,1)+"Mb";
		}
		if (bytes < 1024*1024*1024*1024) {
			return round(bytes/1024/1024/1024,1)+"Gb";
		}
		return round(bytes/1024/1024/1024/1024,1)+"Tb";
	}
	
}
