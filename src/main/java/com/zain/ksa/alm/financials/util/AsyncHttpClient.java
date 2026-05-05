package com.zain.ksa.alm.financials.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncHttpClient {
	private static final Logger LOGGER = LogManager.getLogger(AsyncHttpClient.class);

	@Async
	public void httpPOST(String httpsURL, String payLoad) {
		LOGGER.info("Started async HTTP POST");
		HashMap<Object, Object> responseMap = new HashMap<>();
		try {
			InputStreamReader in;
			URL obj = new URL(httpsURL);
			HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setReadTimeout(45000);
			conn.setConnectTimeout(30000);
			conn.setRequestProperty("content-type", "application/json");
			OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
			osw.write(payLoad);
			osw.flush();
			osw.close();
			String res = "";
			try {
				in = new InputStreamReader(conn.getInputStream(), "UTF-8");
			} catch (IOException e) {
				LOGGER.warn("Failed to get input stream, using error stream: {}", e.getMessage());
				in = new InputStreamReader(conn.getErrorStream(), "UTF-8");
			}
			try (Reader reader = new BufferedReader(in)) {
				int c;
				for (; (c = reader.read()) >= 0; res = res + (char) c)
					;
			}
			int conresponse = conn.getResponseCode();
			LOGGER.info("Microservice URL: {}", httpsURL);
			LOGGER.info("Warehouse response: {}", res);
			responseMap.put("mqresponse", res);
			responseMap.put("mqresponseCode", Integer.valueOf(conresponse));
			if (conresponse == 200) {
				responseMap.put("mqStatus", "000");
				responseMap.put("mqStatusDescription", "Successful");
			} else {
				responseMap.put("mqStatus", "057");
				responseMap.put("mqStatusDescription", "Not Successful, response code (" + conresponse + ")");
			}
		} catch (IOException ex) {
			LOGGER.error("Exception during async HTTP POST: {}", ex.getMessage(), ex);
			if (ex.toString().contains("TimeoutException")) {
				responseMap.put("mqStatus", "091");
				responseMap.put("mqStatusDescription", "Not Successful TimeoutException: Cannot reach USSD Receiver");
			} else {
				responseMap.put("mqStatus", "057");
				responseMap.put("mqStatusDescription", "Not Successful");
			}
		}
	}
}
