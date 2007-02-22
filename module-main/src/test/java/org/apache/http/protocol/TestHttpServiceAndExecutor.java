/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.protocol;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.message.HttpGet;
import org.apache.http.message.HttpPost;
import org.apache.http.mockup.TestHttpClient;
import org.apache.http.mockup.TestHttpServer;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.EntityUtils;

import junit.framework.*;

public class TestHttpServiceAndExecutor extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestHttpServiceAndExecutor(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestHttpServiceAndExecutor.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestHttpServiceAndExecutor.class);
    }

    private TestHttpServer server;
    private TestHttpClient client;
    
    protected void setUp() throws Exception {
        this.server = new TestHttpServer();
        this.client = new TestHttpClient();
    }

    protected void tearDown() throws Exception {
        this.server.shutdown();
    }

    /**
     * This test case executes a series of simple GET requests 
     */
    public void testSimpleHttpGets() throws Exception {
        
        int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        final List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                String s = request.getRequestLine().getUri();
                if (s.startsWith("/?")) {
                    s = s.substring(2);
                }
                int index = Integer.parseInt(s);
                byte[] data = (byte []) testData.get(index);
                ByteArrayEntity entity = new ByteArrayEntity(data); 
                response.setEntity(entity);
            }
            
        });
        
        this.server.start();
        
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());
        
        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }
                
                HttpGet get = new HttpGet("/?" + r);
                HttpResponse response = this.client.execute(get, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = (byte[]) testData.get(r);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple POST requests with content length 
     * delimited content. 
     */
    public void testSimpleHttpPostsWithContentLength() throws Exception {
        
        int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        });
        
        this.server.start();
        
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());
        
        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }
                
                HttpPost post = new HttpPost("/");
                byte[] data = (byte[]) testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = (byte[]) testData.get(r);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple POST requests with chunk 
     * coded content content. 
     */
    public void testSimpleHttpPostsChunked() throws Exception {
        
        int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(20000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        });
        
        this.server.start();
        
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());
        
        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }
                
                HttpPost post = new HttpPost("/");
                byte[] data = (byte[]) testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                outgoing.setChunked(true);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = (byte[]) testData.get(r);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple HTTP/1.0 POST requests. 
     */
    public void testSimpleHttpPostsHTTP10() throws Exception {
        
        int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        });
        
        this.server.start();
        
        // Set protocol level to HTTP/1.0
        this.client.getParams().setParameter(
                HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
        
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());
        
        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }
                
                HttpPost post = new HttpPost("/");
                byte[] data = (byte[]) testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                assertEquals(HttpVersion.HTTP_1_0, response.getStatusLine().getHttpVersion());
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = (byte[]) testData.get(r);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple POST requests using 
     * the 'expect: continue' handshake. 
     */
    public void testHttpPostsWithExpectContinue() throws Exception {
        
        int reqNo = 20;
        
        Random rnd = new Random();
        
        // Prepare some random data
        List testData = new ArrayList(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content"); 
                    response.setEntity(outgoing);
                }
            }
            
        });
        
        this.server.start();
        
        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, true);
        
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());
        
        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }
                
                HttpPost post = new HttpPost("/");
                byte[] data = (byte[]) testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                outgoing.setChunked(true);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = (byte[]) testData.get(r);
                
                assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }
    
    /**
     * This test case executes a series of simple POST requests that do not 
     * meet the target server expectations. 
     */
    public void testHttpPostsWithExpectationVerification() throws Exception {
        
        int reqNo = 3;
        
        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                
                StringEntity outgoing = new StringEntity("No content"); 
                response.setEntity(outgoing);
            }
            
        });
        
        this.server.setExpectationVerifier(new HttpExpectationVerifier() {

            public void verify(
                    final HttpRequest request, 
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException {
                Header someheader = request.getFirstHeader("Secret");
                if (someheader != null) {
                    int secretNumber;
                    try {
                        secretNumber = Integer.parseInt(someheader.getValue());
                    } catch (NumberFormatException ex) {
                        response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                        return;
                    }
                    if (secretNumber < 2) {
                        response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
                        ByteArrayEntity outgoing = new ByteArrayEntity(
                                EncodingUtils.getAsciiBytes("Wrong secret number")); 
                        response.setEntity(outgoing);
                    }
                }
            }
            
        });
        
        this.server.start();
        
        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, true);
        
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());
        
        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }
                
                HttpPost post = new HttpPost("/");
                post.addHeader("Secret", Integer.toString(r));
                ByteArrayEntity outgoing = new ByteArrayEntity(
                        EncodingUtils.getAsciiBytes("No content")); 
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);

                HttpEntity entity = response.getEntity();
                assertNotNull(entity);
                entity.consumeContent();
                
                if (r < 2) {
                    assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getStatusLine().getStatusCode());
                } else {
                    assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }
                
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }
    
}
