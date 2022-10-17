package org.graalvm.example;

import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

public class NettyExample {

	public static void test() {
		ServerCookieDecoder.STRICT.decode("");
	}

}
