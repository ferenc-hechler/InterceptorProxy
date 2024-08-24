package de.hechler.interceptor.https;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {

	public static String now() {
		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
		return fmt.format(new Date());
	}
	
}
