/*******************************************************************************
 * Copyright 2013 Johns Hopkins University
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package edu.jhu.pha.vospace.process;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.log4j.Logger;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.rabbitmq.client.QueueingConsumer;
import edu.jhu.pha.vospace.QueueConnector;
import edu.jhu.pha.vospace.SettingsServlet;
import edu.jhu.pha.vospace.node.Node;
import edu.jhu.pha.vospace.node.NodeFactory;
import edu.jhu.pha.vospace.node.VospaceId;
import edu.jhu.pha.vospace.oauth.UserHelper;
import edu.jhu.pha.vospace.process.sax.AsciiTableContentHandler;

public class NodeProcessor extends Thread {

	private static final Logger logger = Logger.getLogger(NodeProcessor.class);

    static Configuration conf = SettingsServlet.getConfig();

	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    
	public static XMLConfiguration processorConf;
	
	static {
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		try	{
			processorConf = new XMLConfiguration("processors.xml");
			processorConf.setExpressionEngine(new XPathExpressionEngine());
		} catch(ConfigurationException ex) {
		    logger.error("Error reading the nodes processor configuration file processors.xml." + ex.getMessage());
		}
	}
	
    @Override
	public void run() {
    	
    	final Thread currentThread = this;
    	
		QueueConnector.goAMQP("nodesProcessor", new QueueConnector.AMQPWorker<Boolean>() {
			@Override
			public Boolean go(com.rabbitmq.client.Connection conn, com.rabbitmq.client.Channel channel) throws IOException {

				channel.exchangeDeclare(conf.getString("process.exchange.nodeprocess"), "fanout", true);
				channel.exchangeDeclare(conf.getString("vospace.exchange.nodechanged"), "fanout", false);

				channel.queueDeclare(conf.getString("process.queue.nodeprocess"), true, false, false, null);
				
				channel.queueBind(conf.getString("process.queue.nodeprocess"), conf.getString("process.exchange.nodeprocess"), "");

				QueueingConsumer consumer = new QueueingConsumer(channel);
				channel.basicConsume(conf.getString("process.queue.nodeprocess"), false, consumer);
				
				while (!currentThread.isInterrupted()) {

	            	try {
				    	QueueingConsumer.Delivery delivery = consumer.nextDelivery();
				    	
				    	Map<String,Object> nodeData = (new ObjectMapper()).readValue(delivery.getBody(), 0, delivery.getBody().length, Map.class);

		            	channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
						final Node node = NodeFactory.getNode(new VospaceId((String)nodeData.get("uri")), (String)nodeData.get("owner"));

		            	logger.debug("Node changed: "+nodeData.get("uri")+" "+nodeData.get("owner")+" "+node.getType());
		            	
		            	
		            	switch(node.getType()) {
			            	case DATA_NODE: {
			            		Metadata nodeTikaMeta = new Metadata();
			            		nodeTikaMeta.set(TikaCoreProperties.SOURCE,node.getUri().toString());
			            		nodeTikaMeta.set("owner",(String)nodeData.get("owner"));
			            		nodeTikaMeta.set(TikaCoreProperties.TITLE,node.getUri().getNodePath().getNodeName());
			            		nodeTikaMeta.add(TikaCoreProperties.METADATA_DATE,dateFormat.format(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime()));

			            		TikaInputStream inp = null;
			            		try {
			            			inp = TikaInputStream.get(node.exportData());
				                    MediaType type = new DefaultDetector().detect(inp, nodeTikaMeta);
				                    nodeTikaMeta.set(Metadata.CONTENT_TYPE, type.toString());
				            		node.getNodeInfo().setContentType(nodeTikaMeta.get(HttpHeaders.CONTENT_TYPE));
				            		node.getMetastore().storeInfo(node.getUri(), node.getNodeInfo());
			            		} catch(Exception ex) {
			            			logger.error("Error detecting file type: "+ex.toString());
			            		} finally {
			            			try {inp.close();} catch(Exception ex) {};
			            		}
			            		
			        			String[] processorIds = processorConf.getStringArray("//processor[mimetype='"+nodeTikaMeta.get(Metadata.CONTENT_TYPE)+"']/id");
			            		
			            		try {
			            			
			            			for(String processorId: processorIds) {
			            				String conf = processorConf.getString("//processor[id='"+processorId+"']/config");
			            				AbstractParser parser;
		            					TikaConfig config = TikaConfig.getDefaultConfig();
			            				if(conf != null) {
			            					config = new TikaConfig(getClass().getResourceAsStream(conf));
			            				}
			            				
										parser = new CompositeParser(config.getMediaTypeRegistry(), config.getParser());
			            			
			            				String handlerName =processorConf.getString("//processor[id='"+processorId+"']/handler"); 
			            				ContentHandler handler;
			            				if(null != handlerName)
			            					handler = (ContentHandler) Class.forName(handlerName).getConstructor().newInstance();
			            				else
			            					handler = new BodyContentHandler();
			            				
			            				InputStream str = null;
			            				try {
			            					str = TikaInputStream.get(node.exportData());
					            			parser.parse(str,
					            					handler,
					            			        nodeTikaMeta,
					            			        new ParseContext());
			            				} finally {
					            			try {str.close();} catch(Exception ex) {}
					            		}				            			
			            				
					            		// now do out-of-tika processing of results
			            				String processorName =processorConf.getString("//processor[id='"+processorId+"']/processor"); 
					        			if(null != processorName) {
					        				try {
					        					Class handlerClass = Class.forName(processorName);
					        					Method processMetaMethod = handlerClass.getMethod("processNodeMeta", Metadata.class, Object.class, JsonNode.class);
					        					
					        					JsonNode credentialsNode = UserHelper.getProcessorCredentials(node.getOwner(), processorId);
					        					if(credentialsNode != null) {
					        						processMetaMethod.invoke(handlerClass, nodeTikaMeta, handler, credentialsNode);
						        					logger.debug("Processing of "+node.getUri().toString()+" is finished.");
					        					} else {
						        					logger.debug("User doesn't have credentials setup for processing of "+node.getUri().toString());
					        					}
					        					
					        				} catch (Exception e) {
					        					logger.error("Error processing the node. "+e.getMessage());
					        					e.printStackTrace();
					        				}
					        			}

			            			}

				            		logger.debug("Updated node "+node.getUri().toString()+" to "+node.getNodeInfo().getContentType()+" and "+node.getNodeInfo().getSize());
				            		
				            		try {
					        			nodeData.put("container", node.getUri().getNodePath().getParentPath().getNodeStoragePath());
					        			byte[] jobSer = (new ObjectMapper()).writeValueAsBytes(nodeData);
					        			channel.basicPublish(conf.getString("vospace.exchange.nodechanged"), "", null, jobSer);
					        		} catch (IOException e) {
					        			logger.error(e);
					        		}
			            		} catch(TikaException ex) {
			            			logger.error("Error parsing the node "+node.getUri().toString()+": "+ex.getMessage());
			            			ex.printStackTrace();
			            		} catch(SAXException ex) {
			            			logger.error("Error SAX parsing the node "+node.getUri().toString()+": "+ex.getMessage());
			            		} catch(IOException ex) {
			            			logger.error("Error reading the node "+node.getUri().toString()+": "+ex.getMessage());
			            		}
			            		
			            		break;
			            	}
			            	case CONTAINER_NODE: {
//				            	DbPoolServlet.goSql("Processing nodes",
//			                		"select * from nodes where owner = ?)",
//			                        new SqlWorker<Boolean>() {
//			                            @Override
//			                            public Boolean go(java.sql.Connection conn, java.sql.PreparedStatement stmt) throws SQLException {
//			                            	stmt.setString(1, node.getOwner());
//			                                /*ResultSet resSet = stmt.executeQuery();
//			                                while(resSet.next()) {
//			                                	String uriStr = resSet.getString(1);
//			                                	String username = resSet.getString(2);
//			                                	
//			                                	try {
//				                                	VospaceId uri = new VospaceId(uriStr);
//				                                	
//				                                	Node newNode = NodeFactory.getInstance().getNode(uri, username);
//				                                	newNode.remove();
//			                                	} catch(Exception ex) {
//			                                		ex.printStackTrace();
//			                                	}
//			                                }*/
//			                            	return true;
//			                            }
//			                        }
//			            	
//				                );
			            		break;
			            	}
			            	default: {
			            		break;
			            	}
		            	}
		            	
	            	} catch(InterruptedException ex) {
	            		logger.error("Sleeping interrupted. "+ex.getMessage());
	            	} catch(JsonMappingException ex) {
	            		logger.error("Error reading the changed node JSON: "+ex.getMessage());
	            	} catch(JsonParseException ex) {
	            		logger.error("Error reading the changed node JSON: "+ex.getMessage());
	            	} catch (IOException ex) {
	            		ex.printStackTrace();
	            		logger.error("Error reading the changed node JSON: "+ex.getMessage());
					} catch (URISyntaxException ex) {
	            		logger.error("Error parsing VospaceId from changed node JSON: "+ex.getMessage());
					} catch (Exception ex) {
						ex.printStackTrace();
	            		logger.error("Error parsing fits: "+ex.getMessage());
					}
	            }

		    	
		    	return true;
			}
		});

    }
    
}


