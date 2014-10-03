/*
 * Copyright 2008 Sean Sullivan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.oauth.client.httpclient4;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import net.oauth.client.ExcerptInputStream;
import net.oauth.http.HttpMessage;
import net.oauth.http.HttpResponseMessage;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * Utility methods for an OAuth client based on the <a
 * href="http://hc.apache.org">Apache HttpClient</a>.
 * 
 * @author Sean Sullivan
 */
public class HttpClient4 implements net.oauth.http.HttpClient {

    public HttpClient4() {
        this(SHARED_CLIENT);
    }

    public HttpClient4(HttpClientPool clientPool) {
        this.clientPool = clientPool;
    }
    
    public HttpClient4(java.io.InputStream is){
        
        

         this(SHARED_CLIENT);
         //org.apache.http.entity.FileEntity reqEntity = new org.apache.http.entity.FileEntity(new File(is), "binary/octet-stream");
        //this.fent= new org.apache.http.entity.FileEntity();
    }

    //private final org.apache.http.entity.FileEntity fent;
    private final HttpClientPool clientPool;

    @Override
    public HttpResponseMessage execute(HttpMessage request, Map<String, Object> parameters) throws IOException {
        System.out.println("*** I am here !!!!");
        final String method = request.method;
        final String url = request.url.toExternalForm();
        final InputStream body = request.getBody();
        final boolean isDelete = DELETE.equalsIgnoreCase(method);
        final boolean isPost = POST.equalsIgnoreCase(method);
        final boolean isPut = PUT.equalsIgnoreCase(method);
        byte[] excerpt = null;
        HttpRequestBase httpRequest;
        if (isPost || isPut) {
            HttpEntityEnclosingRequestBase entityEnclosingMethod = isPost ? new HttpPost(url) : new HttpPut(url);
            if (body != null) {
                ExcerptInputStream e = new ExcerptInputStream(body);
                excerpt = e.getExcerpt();
                String length = request.removeHeaders(HttpMessage.CONTENT_LENGTH);
                System.out.println("length:"+length);
                entityEnclosingMethod
                        .setEntity(new InputStreamEntity(e, (length == null) ? -1 : Long.parseLong(length)));
            }
            httpRequest = entityEnclosingMethod;
        } else if (isDelete) {
            httpRequest = new HttpDelete(url);
        } else {
            httpRequest = new HttpGet(url);
        }
        for (Map.Entry<String, String> header : request.headers) {
            httpRequest.addHeader(header.getKey(), header.getValue());
        }
        HttpParams params = httpRequest.getParams();
        for (Map.Entry<String, Object> p : parameters.entrySet()) {
            String name = p.getKey();
            String value = p.getValue().toString();
            if (FOLLOW_REDIRECTS.equals(name)) {
                params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, Boolean.parseBoolean(value));
            } else if (READ_TIMEOUT.equals(name)) {
                params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, Integer.parseInt(value));
            } else if (CONNECT_TIMEOUT.equals(name)) {
                params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, Integer.parseInt(value));
            }
        }
        
        params.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE,false); 

        //System.out.println("url:"+httpRequest.getURI().toString());        
        HttpClient client = clientPool.getHttpClient(new URL(httpRequest.getURI().toString()));
        
        HttpResponse httpResponse = null;
        try{
        System.out.println("before:"+client.getConnectionManager());
        httpResponse = client.execute(httpRequest);
        System.out.println("after:"+client);
        }catch(Exception exp){System.out.println("Exception here execute:"+exp.getMessage());
        exp.printStackTrace();
        }
        return new HttpMethodResponse(httpRequest, httpResponse, excerpt, request.getContentCharset());
    }

    private static final HttpClientPool SHARED_CLIENT = new SingleClient();

    /**
     * A pool that simply shares a single HttpClient. An HttpClient owns a pool
     * of TCP connections. So, callers that share an HttpClient will share
     * connections. Sharing improves performance (by avoiding the overhead of
     * creating connections) and uses fewer resources in the client and its
     * servers.
     */
    private static class SingleClient implements HttpClientPool {
        SingleClient() {
            HttpClient client1 = new DefaultHttpClient();
            ClientConnectionManager mgr = client1.getConnectionManager();
            if (!(mgr instanceof ThreadSafeClientConnManager)) {
                HttpParams params = client1.getParams();
                ThreadSafeClientConnManager man = new ThreadSafeClientConnManager(params, mgr.getSchemeRegistry());
                man.setDefaultMaxPerRoute(5);
                client1 = new DefaultHttpClient(man, params);
                
            }
            this.client = client1;
        }

        private final HttpClient client;

        @Override
        public HttpClient getHttpClient(URL server) {            
            return client;
            
        }
    }
}