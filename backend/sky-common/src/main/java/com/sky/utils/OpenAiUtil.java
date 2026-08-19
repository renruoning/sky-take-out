package com.sky.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 接口调用工具类
 */
public class OpenAiUtil {

    private static final int TIMEOUT_MSEC = 30 * 1000;

    /**
     * 发起一次对话补全请求，返回AI回复的文本内容
     *
     * @param apiKey  OpenAI API Key
     * @param model   模型名称，如 gpt-4o-mini
     * @param baseUrl Chat Completions接口地址
     * @param messages 完整对话历史，每条形如 {"role": "user"/"assistant"/"system", "content": "..."}
     * @return AI回复的文本内容
     * @throws IOException 请求失败或响应格式不符合预期时抛出，异常信息里带上响应体方便定位问题
     */
    public static String chat(String apiKey, String model, String baseUrl, List<Map<String, String>> messages) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        String resultString;

        try {
            HttpPost httpPost = new HttpPost(baseUrl);
            httpPost.setHeader("Authorization", "Bearer " + apiKey);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setConfig(RequestConfig.custom()
                    .setConnectTimeout(TIMEOUT_MSEC)
                    .setConnectionRequestTimeout(TIMEOUT_MSEC)
                    .setSocketTimeout(TIMEOUT_MSEC)
                    .build());

            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray messagesArray = new JSONArray();
            messagesArray.addAll(messages);
            body.put("messages", messagesArray);

            StringEntity entity = new StringEntity(body.toJSONString(), "utf-8");
            entity.setContentEncoding("utf-8");
            entity.setContentType("application/json");
            httpPost.setEntity(entity);

            response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            resultString = EntityUtils.toString(response.getEntity(), "UTF-8");

            if (statusCode != 200) {
                throw new IOException("OpenAI接口返回异常状态码：" + statusCode + "，响应体：" + resultString);
            }

            JSONObject resultJson = JSONObject.parseObject(resultString);
            JSONArray choices = resultJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("OpenAI接口响应中没有choices字段，响应体：" + resultString);
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            return message.getString("content");
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            httpClient.close();
        }
    }
}
