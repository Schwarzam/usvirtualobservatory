/*******************************************************************************
 * Copyright (c) 2011, Johns Hopkins University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Johns Hopkins University nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Johns Hopkins University BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package edu.jhu.pha.vosync.rest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.Vector;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.TokenBuffer;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import edu.jhu.pha.vospace.DbPoolServlet;
import edu.jhu.pha.vospace.SettingsServlet;
import edu.jhu.pha.vospace.DbPoolServlet.SqlWorker;
import edu.jhu.pha.vospace.api.AccountInfo;
import edu.jhu.pha.vospace.jobs.JobsProcessor;
import edu.jhu.pha.vospace.meta.MetaStoreDistributed;
import edu.jhu.pha.vospace.meta.MetaStore;
import edu.jhu.pha.vospace.meta.MetaStoreFactory;
import edu.jhu.pha.vospace.meta.RegionsInfo;
import edu.jhu.pha.vospace.node.ContainerNode;
import edu.jhu.pha.vospace.node.DataNode;
import edu.jhu.pha.vospace.node.Node;
import edu.jhu.pha.vospace.node.NodeFactory;
import edu.jhu.pha.vospace.node.NodePath;
import edu.jhu.pha.vospace.node.NodeType;
import edu.jhu.pha.vospace.node.VospaceId;
import edu.jhu.pha.vospace.node.Node.Detail;
import edu.jhu.pha.vospace.oauth.DropboxAccessLevel;
import edu.jhu.pha.vospace.oauth.UserHelper;
import edu.jhu.pha.vospace.rest.JobDescription;
import edu.jhu.pha.vospace.rest.JobDescription.DIRECTION;
import edu.jhu.pha.vosync.exception.BadRequestException;
import edu.jhu.pha.vosync.exception.ForbiddenException;
import edu.jhu.pha.vosync.exception.InternalServerErrorException;
import edu.jhu.pha.vosync.exception.NotAcceptableException;
import edu.jhu.pha.vosync.exception.NotFoundException;

/**
 * @author Dmitry Mishin
 */
@Path("/1/")
public class DropboxService {
	
	private static final Logger logger = Logger.getLogger(DropboxService.class);
	private @Context ServletContext context;
	private @Context HttpServletRequest request;
	private @Context HttpServletResponse response;
	private static final Configuration conf = SettingsServlet.getConfig();

	private static final JsonFactory f = new JsonFactory();
	
	private static final double GIGABYTE = 1024.0*1024.0*1024.0;

	
	@Path("fileops/copy")
	@POST
	public Response copy(@FormParam("root") String root, @FormParam("from_path") String fromPath, @FormParam("to_path") String toPath) {
		final String username = (String)request.getAttribute("username");
		
		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		if(null == root || root.isEmpty())
			throw new BadRequestException("Not found parameter root");
		
		if(null == fromPath || fromPath.isEmpty())
			throw new BadRequestException("Not found parameter fromPath");
		
		if(null == toPath || toPath.isEmpty())
			throw new BadRequestException("Not found parameter toPath");
		
		VospaceId fromId, toId;
		try {
			fromId = new VospaceId(new NodePath(fromPath, (String)request.getAttribute("root_container")));
			toId = new VospaceId(new NodePath(toPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(fromId, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(fromId.getNodePath().getNodeStoragePath());
		}
		node.copy(toId);
		
		return Response.ok(NodeFactory.getInstance().getNode(toId, username).export("json-dropbox",Detail.min)).build();
	}

	@Path("fileops/create_folder")
	@POST
	public Response createFolder(@FormParam("root") String root, @FormParam("path") String path) {
		logger.debug("Creating folder "+path);
		final String username = (String)request.getAttribute("username");
		
		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		if(null == root || root.isEmpty())
			throw new BadRequestException("Not found parameter root");
		
		if(null == path)
			throw new BadRequestException("Not found parameter path");
		
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(path, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		Node node = NodeFactory.getInstance().createNode(identifier, username, NodeType.CONTAINER_NODE);
		
		node.createParent();
		node.setNode(null);

		return Response.ok(node.export("json-dropbox",Detail.min)).build();
	}

	@Path("fileops/delete")
	@POST
	public Response delete(@FormParam("root") String root, @FormParam("path") String path) {
		final String username = (String)request.getAttribute("username");

		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		if(null == root || root.isEmpty())
			throw new BadRequestException("Not found parameter root");
		
		if(null == path || path.isEmpty())
			throw new BadRequestException("Not found parameter path");
		
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(path, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}
		node.markRemoved();
		
		return Response.ok(NodeFactory.getInstance().getNode(identifier, username).export("json-dropbox",Detail.min)).build();
	}

	@GET @Path("account/info")
	public Response getAccountInfo() {
		try {
	    	ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			JsonGenerator g2 = f.createJsonGenerator(byteOut).useDefaultPrettyPrinter();
	
			AccountInfo info = UserHelper.getAccountInfo((String)request.getAttribute("username"));
			
			g2.writeStartObject();
			
			g2.writeStringField("referral_link", "");
			g2.writeStringField("display_name", info.getUsername());
			g2.writeStringField("uid", "0");
			g2.writeStringField("country", "US");
			
			g2.writeFieldName("quota_info");
			g2.writeStartObject();
			
			g2.writeNumberField("shared",0);
			g2.writeNumberField("quota",info.getSoftLimit());
			g2.writeNumberField("normal",info.getBytesUsed()/GIGABYTE);
			
			g2.writeEndObject();
			
			g2.close();
			byteOut.close();
	
			return Response.ok(byteOut.toByteArray()).build();
		} catch(IOException ex) {
			throw new InternalServerErrorException(ex.getMessage());
		}
	}
	
	@PUT @Path("account/service")
	public Response setAccountService(InputStream serviceCredInpStream) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode credNode = mapper.readTree(serviceCredInpStream);
			UserHelper.updateUserService((String)request.getAttribute("username"), credNode);
			return Response.ok().build();
		} catch(IOException ex) {
			throw new InternalServerErrorException(ex.getMessage());
		}
	}
	
	@GET @Path("account/service")
	public Response getAccountService() {
		return Response.ok(UserHelper.getUserServices((String)request.getAttribute("username")).toString()).build();
	}
	
	@GET @Path("regions/info")
	public Response getRegionsInfo() {
		MetaStore mstore = MetaStoreFactory.getInstance().getMetaStore(null);
		if(mstore instanceof MetaStoreDistributed) {
			RegionsInfo regionsInfo = ((MetaStoreDistributed)mstore).getRegionsInfo();
			return Response.ok(regionsInfo.toJson()).build();
		}
		throw new InternalServerErrorException("Unsupported");
	}
	
	@GET @Path("files/{root:dropbox|sandbox}/{path:.+}")
	public Response getFile(@PathParam("root") String root, @PathParam("path") String fullPath) {
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}

		response.setHeader("Content-Disposition", "attachment; filename="+identifier.getNodePath().getNodeName());
		response.setHeader("Content-Length", Long.toString(node.getNodeInfo().getSize()));
		
		InputStream nodeInputStream;
		try {
			nodeInputStream = node.exportData(); 
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			logger.error("Node "+node.getUri().toString()+" data  not found.");
			throw new NotFoundException(identifier.getId().toASCIIString());
		}
		
		logger.debug("Node "+node.getUri().toString()+" size: "+node.getNodeInfo().getSize());
		
		ResponseBuilder response = Response.ok(nodeInputStream);
		response.header("x-dropbox-metadata", new String((byte[])(node.export("json-dropbox", Detail.min))));
		
		return response.build();
	}

	@GET @Path("regions/{path:.+}")
	public Response getFileRegions(@PathParam("path") String fullPath) {
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}
		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}

		if(node.getType() == NodeType.CONTAINER_NODE) {
			List<String> nodeRegions = ((ContainerNode)node).getNodeRegions();
			StringBuilder builder = new StringBuilder();
			builder.append("[");
			boolean first = true;
			for(String region: nodeRegions) {
				if(!first)
					builder.append(",");
				else
					first = false;
				builder.append("{\"id\": \""+region+"\"}");
			}
			
			builder.append("]");
			return Response.ok(builder.toString()).build();
		} else {
			throw new BadRequestException("ContainerNotFound");
		}
	}
	
	@GET @Path("metadata/{root:dropbox|sandbox}/{path:.+}")
	public Response getFileMetadata(@PathParam("root") String root, @PathParam("path") String fullPath, @QueryParam("list") @DefaultValue("true") Boolean list, @QueryParam("file_limit") @DefaultValue("25000") int file_limit,  @QueryParam("start") @DefaultValue("0") int start, @QueryParam("count") @DefaultValue("-1") int count) {
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			logger.debug(e.getMessage());
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}
		
		Detail detailLevel = list?Detail.max:Detail.min;
		
		long time = System.currentTimeMillis();
		byte[] nodeExport;
		try {
			if(node.getType() == NodeType.CONTAINER_NODE) {
				nodeExport = (byte[])(((ContainerNode)node).export("json-dropbox", detailLevel, start, count));
			} else {
				nodeExport = (byte[])(node.export("json-dropbox", detailLevel));
			}
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getId().toASCIIString());
		}
		logger.debug("Generated node contents in "+(System.currentTimeMillis()-time)/1000.0);
		
		return Response.ok(nodeExport).build();
	}

	@GET @Path("metadata/{root:dropbox|sandbox}")
	public Response getRootMetadata(@PathParam("root") String root, @QueryParam("list") @DefaultValue("true") Boolean list) {
		return getFileMetadata(root, "", list, 25000, 0, -1);
	}
	
	@GET @Path("transfers/info")
	@Produces(MediaType.APPLICATION_JSON)
	public byte[] getTransfersInfo() {
	    return DbPoolServlet.goSql("Get transfers queue",
	    		"select id, state, direction, starttime, endtime, target from jobs JOIN user_identities ON jobs.user_id = user_identities.user_id WHERE identity = ?",
	            new SqlWorker<byte[]>() {
	                @Override
	                public byte[] go(Connection conn, PreparedStatement stmt) throws SQLException {
	                	
	                	stmt.setString(1, (String)request.getAttribute("username"));
	                	
	                	ByteArrayOutputStream byteOut = null;
	                	try {
		                	JsonFactory f = new JsonFactory();
					    	byteOut = new ByteArrayOutputStream();
							JsonGenerator g2 = f.createJsonGenerator(byteOut);
	
	        				g2.writeStartArray();

	        				ResultSet resSet = stmt.executeQuery();
		        			while(resSet.next()) {
		        				
		        				g2.writeStartObject();
		        				g2.writeStringField("id", resSet.getString("id"));
		        				g2.writeStringField("state", resSet.getString("state"));
		        				g2.writeStringField("direction", resSet.getString("direction"));
		        				g2.writeStringField("starttime", (null != resSet.getTimestamp("starttime")?resSet.getTimestamp("starttime").toString():""));
		        				g2.writeStringField("endtime", (null != resSet.getTimestamp("endtime")?resSet.getTimestamp("starttime").toString():""));
		        				g2.writeStringField("path", resSet.getString("target"));
		        				g2.writeEndObject();
		        	    	}

		        			g2.writeEndArray();
		        			g2.close();
            				byteOut.close();
	        				
		        			return byteOut.toByteArray();
	            		} catch(IOException ex) {
	            			throw new InternalServerErrorException(ex);
	            		}
	                }
	            }
	    );
	}
	
	@Path("fileops/move")
	@POST
	public Response move(@FormParam("root") String root, @FormParam("from_path") String fromPath, @FormParam("to_path") String toPath) {
		final String username = (String)request.getAttribute("username");
		
		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		if(null == root || root.isEmpty())
			throw new BadRequestException("Not found parameter root");
		
		if(null == fromPath || fromPath.isEmpty())
			throw new BadRequestException("Not found parameter fromPath");
		
		if(null == toPath || toPath.isEmpty())
			throw new BadRequestException("Not found parameter toPath");
		
		VospaceId fromId, toId;
		try {
			fromId = new VospaceId(new NodePath(fromPath, (String)request.getAttribute("root_container")));
			toId = new VospaceId(new NodePath(toPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(fromId, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(fromId.getNodePath().getNodeStoragePath());
		}
		node.move(toId);
		
		return Response.ok(NodeFactory.getInstance().getNode(toId, username).export("json-dropbox",Detail.min)).build();
	}

	@POST @Path("files/{root:dropbox|sandbox}/{path:.+}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	/*TODO Test the method */
	public Response postFile(@PathParam("root") String root, @PathParam("path") String fullPath, @FormDataParam("file") InputStream fileDataInp, @FormDataParam("file") FormDataContentDisposition fileDetail, @QueryParam("overwrite") @DefaultValue("true") Boolean overwrite) {
		final String username = (String)request.getAttribute("username");

		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		MetaStore metastore = MetaStoreFactory.getInstance().getMetaStore(username);
		
		Node node;
		if(identifier.getNodePath().getParentPath().isRoot(false)){
			throw new NotFoundException("Is a root folder");
		} else {
			if(!overwrite) {
				if(metastore.isStored(identifier)){
					logger.debug("Found conflict in node "+identifier);
					String currentFile = identifier.toString();
					String fileName = currentFile.substring(currentFile.lastIndexOf("/")+1);
					if(fileName.contains("."))
						fileName = fileName.substring(0,fileName.lastIndexOf('.'));
					
					int current_num = 1;
					try {
						VospaceId newId = new VospaceId(currentFile.replaceAll(fileName, fileName+"_"+current_num++));
						while(metastore.isStored(newId)){
							logger.debug("Node "+newId.toString()+" exists.");
							newId = new VospaceId(currentFile.replaceAll(fileName, fileName+"_"+current_num++));
						}
						logger.debug("Node "+newId.toString()+" not exists.");
						node = (DataNode)NodeFactory.getInstance().createNode(newId, username, NodeType.DATA_NODE);
						node.createParent();
						node.setNode(null);
					} catch(URISyntaxException e) {
						throw new InternalServerErrorException("InvalidURI");
					}
				} else {
					node = (DataNode)NodeFactory.getInstance().createNode(identifier, username, NodeType.DATA_NODE);
					node.createParent();
					node.setNode(null);
				}
			} else {
				try {
					node = NodeFactory.getInstance().getNode(identifier, username);
				} catch(NotFoundException ex) {
					node = (DataNode)NodeFactory.getInstance().createNode(identifier, username, NodeType.DATA_NODE);
					node.createParent();
					node.setNode(null);
				}
			}
		}
		
		if(!(node instanceof DataNode)) {
			throw new NotFoundException("Is a container");
		}
		
		((DataNode)node).setData(fileDataInp);
		
		return Response.ok(node.export("json-dropbox",Detail.max)).build();
	}

	@PUT @Path("files_put/{root:dropbox|sandbox}/{path:.+}")
	public Response putFile(@PathParam("root") String root, @PathParam("path") String fullPath, InputStream fileDataInp, @QueryParam("overwrite") @DefaultValue("true") Boolean overwrite, @QueryParam("parent_rev") String parentRev) {
		final String username = (String)request.getAttribute("username");

		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		MetaStore metastore = MetaStoreFactory.getInstance().getMetaStore(username);
		
		Node node;
		if(identifier.getNodePath().getParentPath().isRoot(false)){
			throw new NotFoundException("Is a root folder");
		} else {
			if(!overwrite) {
				if(metastore.isStored(identifier)){
					//throw new BadRequestException(identifier.toString()+" exists.");
					//logger.debug("Found conflict in node "+identifier);
					
					node = NodeFactory.getInstance().getNode(identifier, username);
					
					if(node.getNodeInfo().isDeleted()) {
						logger.debug("Node "+node.getUri().toString()+" is deleted. Recreating the node metadata.");
						node.remove();
						node = (DataNode)NodeFactory.getInstance().createNode(identifier, username, NodeType.DATA_NODE);
						node.setNode(null);
					} else if(!parentRev.equals(node.getNodeInfo().getRevision())) {
						throw new BadRequestException("Revision parameter error");

						//TODO fix the revisions
						/*logger.debug("Revisions do not match: "+parentRev+" "+node.getNodeInfo().getCurrentRev());
					
						String currentFile = identifier.toString();
						String fileName = currentFile.substring(currentFile.lastIndexOf("/")+1);
						if(fileName.contains("."))
							fileName = fileName.substring(0,fileName.lastIndexOf('.'));
						
						int current_num = 1;
						try {
							VospaceId newId = new VospaceId(currentFile.replaceAll(fileName, fileName+"_"+current_num++));
							while(metastore.isStored(newId)){
								newId = new VospaceId(currentFile.replaceAll(fileName, fileName+"_"+current_num++));
							}
							node = (DataNode)NodeFactory.getInstance().getDefaultNode(newId, username);
							node.createParent();
							node.setNode();
						} catch(URISyntaxException e) {
							throw new InternalServerErrorException("InvalidURI");
						}*/
					}
				} else {
					node = (DataNode)NodeFactory.getInstance().createNode(identifier, username, NodeType.DATA_NODE);
					node.createParent();
					node.setNode(null);
				}
			} else {
				try {
					node = NodeFactory.getInstance().getNode(identifier, username);
				} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
					node = (DataNode)NodeFactory.getInstance().createNode(identifier, username, NodeType.DATA_NODE);
					node.createParent();
					node.setNode(null);
				}
			}
		}
		
		if(!(node instanceof DataNode)) {
			throw new NotFoundException("Is a container");
		}
		
		((DataNode)node).setData(fileDataInp);
		Response resp =Response.ok(node.export("json-dropbox",Detail.max)).build(); 
		return resp;
	}
		
	@Path("search/{root:dropbox|sandbox}/{path:.+}")
	@GET
	public byte[] search(@PathParam("root") String root, @PathParam("path") String fullPath, @QueryParam("query") String query, @QueryParam("file_limit") @DefaultValue("1000") int fileLimit, @QueryParam("include_deleted") @DefaultValue("false") boolean includeDeleted) {
		final String username = (String)request.getAttribute("username");

		if(null == query || query.length() < 3){
			throw new BadRequestException("Wrong query parameter");
		}
		
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}
		
		if(!(node instanceof ContainerNode)) {
			throw new NotFoundException("Not a container");
		}
		
		List<VospaceId> nodesList = ((ContainerNode)node).search(query, fileLimit, includeDeleted);

    	TokenBuffer g = new TokenBuffer(null);

		try {
			g.writeStartArray();

			int ind = 0;
			for(VospaceId childNodeId: nodesList) {
				Node childNode = NodeFactory.getInstance().getNode(childNodeId, username);
				JsonNode jnode = (JsonNode)childNode.export("json-dropbox-object", Detail.min); 
				g.writeTree(jnode);

				/*CharBuffer cBuffer = Charset.forName("ISO-8859-1").decode(ByteBuffer.wrap((byte[])childNode.export("json-dropbox", Detail.min)));
				while(cBuffer.remaining() > 0)
					g.writeRaw(cBuffer.get());
				if(ind++ < nodesList.size()-1)
					g.writeRaw(',');*/
			}
			
			g.writeEndArray();

	    	ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
	    	MappingJsonFactory f = new MappingJsonFactory();
			JsonGenerator g2 = f.createJsonGenerator(byteOut).useDefaultPrettyPrinter();
			g.serialize(g2);
			g2.close();
			byteOut.close();

			return byteOut.toByteArray();
		} catch (JsonGenerationException e) {
			e.printStackTrace();
			throw new InternalServerErrorException("Error generationg JSON: "+e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			throw new InternalServerErrorException("Error generationg JSON: "+e.getMessage());
		} finally {
			try { g.close(); } catch(IOException ex) {}
		}
		
	}

	@PUT @Path("regions_put/{path:.+}")
	public Response putFileRegions(@PathParam("path") String fullPath, InputStream regionsInpStream) {
		VospaceId identifier;

		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}
		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}

		if(node.getType() == NodeType.CONTAINER_NODE) {
			try {
				Map<String, String> regions = new HashMap<String, String>();
				
				BufferedReader bufferedRead = new BufferedReader(new InputStreamReader(regionsInpStream));
				
				String curRegion = bufferedRead.readLine();
				String firstRegion = new String(curRegion);
				if(null == curRegion)
					throw new BadRequestException("No regions defined");
				
				String syncRegion = bufferedRead.readLine();
				do {
					regions.put(curRegion, syncRegion);
					if(null != syncRegion) {
						curRegion = new String(syncRegion);
						syncRegion = bufferedRead.readLine();
					}
				} while(null != syncRegion);
				
				if(null != regions.get(firstRegion)) {
					regions.put(curRegion, firstRegion);
				}
				
				bufferedRead.close();
				
				((ContainerNode)node).setNodeRegions(regions);
				
				return Response.ok().build();
			} catch(IOException ex) {
				throw new InternalServerErrorException("Error reading PUT request");
			}
		} else {
			throw new BadRequestException("ContainerNotFound");
		}
	}

	/**
	 * Create new share
	 * @param root
	 * @param fullPath
	 * @param group
	 * @param write_perm
	 * @return
	 */
	@PUT @Path("shares/{root:dropbox|sandbox}/{path:.+}")
	public byte[] putshares(@PathParam("root") String root, @PathParam("path") String fullPath, @QueryParam("group") String group, @DefaultValue("false") @QueryParam("write_perm") Boolean write_perm) {
		final String username = (String)request.getAttribute("username");

		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			logger.debug(e.getMessage());
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}

		
    	ByteArrayOutputStream byteOut = null;
    	try {
        	JsonFactory f = new JsonFactory();
	    	byteOut = new ByteArrayOutputStream();
			JsonGenerator g2 = f.createJsonGenerator(byteOut);

			g2.writeStartObject();
			g2.writeStringField("id", node.getMetastore().createShare(node.getUri(), group, write_perm));
			g2.writeStringField("uri", "");
			g2.writeStringField("expires", "never");
			g2.writeEndObject();

			g2.close();
			byteOut.close();
			
			return byteOut.toByteArray();
		} catch(IOException ex) {
			throw new InternalServerErrorException(ex);
		}
	}

	@GET @Path("shares")
	public byte[] getshares() {
		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		final String username = (String)request.getAttribute("username");
		
    	ByteArrayOutputStream byteOut = null;
    	try {
        	JsonFactory f = new JsonFactory();
	    	byteOut = new ByteArrayOutputStream();
			final JsonGenerator g2 = f.createJsonGenerator(byteOut);

			g2.writeStartArray();

			DbPoolServlet.goSql("Get shares",
	        		"select share_id, container_name, group_name, share_write_permission FROM container_shares "+
	        		"LEFT JOIN groups ON container_shares.group_id = groups.group_id "+
	        		"JOIN containers ON container_shares.container_id = containers.container_id "+
	        		"JOIN user_identities ON containers.user_id = user_identities.user_id WHERE identity = ?",
	                new SqlWorker<Boolean>() {
	                    @Override
	                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
	                    	stmt.setString(1, username);
	            			ResultSet rs = stmt.executeQuery();
	            			while(rs.next()){
	            				try {
	            					g2.writeStartObject();
	            					g2.writeStringField("share_id", rs.getString("share_id"));
	            					g2.writeStringField("container", rs.getString("container_name"));
	            					g2.writeStringField("group", rs.getString("group_name"));
	            					g2.writeBooleanField("write_permission", rs.getBoolean("share_write_permission"));
	            					g2.writeEndObject();
	            				} catch(IOException ex) {logger.error(ex.getMessage());}
	            			}
	            			return true;
	                    }
	                }
	        );
			g2.writeEndArray();

			g2.close();
			byteOut.close();
			return byteOut.toByteArray();
		} catch(IOException ex) {
			throw new InternalServerErrorException(ex);
		}
	}

	@DELETE @Path("shares/{share_id:.+}")
	public Response delete_share(@PathParam("share_id") final String share_id) {
		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		final String username = (String)request.getAttribute("username");
		
			DbPoolServlet.goSql("Remove share",
	        		"delete container_shares from container_shares "+
	        		"JOIN containers ON container_shares.container_id = containers.container_id "+
	        		"JOIN user_identities ON containers.user_id = user_identities.user_id WHERE share_id = ? AND identity = ?;",
	                new SqlWorker<Boolean>() {
	                    @Override
	                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
	                    	stmt.setString(1, share_id);
	                    	stmt.setString(2, username);
	                    	return stmt.execute();
	                    }
	                });
			return Response.ok().build();
	}
	

	@Path("share_groups")
	@GET
	public byte[] share_groups() {
    	ByteArrayOutputStream byteOut = null;
    	try {
        	JsonFactory f = new JsonFactory();
	    	byteOut = new ByteArrayOutputStream();
			final JsonGenerator g2 = f.createJsonGenerator(byteOut);

			g2.writeStartArray();

			DbPoolServlet.goSql("Get share groups",
	        		"select group_id, group_name from groups order by group_name;",
	                new SqlWorker<Boolean>() {
	                    @Override
	                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
	            			ResultSet rs = stmt.executeQuery();
	            			while(rs.next()){
	            				try {
	            					g2.writeStartObject();
	            					g2.writeNumberField("id", rs.getInt(1));
	            					g2.writeStringField("name", rs.getString(2));
	            					g2.writeEndObject();
	            				} catch(IOException ex) {logger.error(ex.getMessage());}
	            			}
	            			return true;
	                    }
	                }
	        );
			g2.writeEndArray();

			g2.close();
			byteOut.close();
			return byteOut.toByteArray();
		} catch(IOException ex) {
			throw new InternalServerErrorException(ex);
		}
		
	}
	
	@Path("share_groups/{group_id}")
	@GET
	public byte[] share_group_members(@PathParam("group_id") final int group_id) {
    	ByteArrayOutputStream byteOut = null;
    	try {
        	JsonFactory f = new JsonFactory();
	    	byteOut = new ByteArrayOutputStream();
			final JsonGenerator g2 = f.createJsonGenerator(byteOut);

			g2.writeStartArray();

			DbPoolServlet.goSql("Get share group members",
	        		"select identity from user_identities JOIN user_groups ON user_identities.user_id = user_groups.user_id WHERE group_id = ? order by identity;",
	                new SqlWorker<Boolean>() {
	                    @Override
	                    public Boolean go(Connection conn, PreparedStatement stmt) throws SQLException {
	                    	stmt.setInt(1, group_id);
	            			ResultSet rs = stmt.executeQuery();
	            			while(rs.next()){
	            				try {
	            					g2.writeString(rs.getString(1));
	            				} catch(IOException ex) {logger.error(ex.getMessage());}
	            			}
	            			return true;
	                    }
	                }
	        );
			g2.writeEndArray();

			g2.close();
			byteOut.close();
			return byteOut.toByteArray();
		} catch(IOException ex) {
			throw new InternalServerErrorException(ex);
		}
		
	}
	
	@Path("media/{root:dropbox|sandbox}/{path:.+}")
	@GET
	public Response media(@PathParam("root") String root, @PathParam("path") String fullPath) {
		final String username = (String)request.getAttribute("username");
		
		VospaceId identifier;
		try {
			identifier = new VospaceId(new NodePath(fullPath, (String)request.getAttribute("root_container")));
		} catch (URISyntaxException e) {
			logger.debug(e.getMessage());
			throw new BadRequestException("InvalidURI");
		}

		Node node;
		try {
			node = NodeFactory.getInstance().getNode(identifier, (String)request.getAttribute("username"));
		} catch(edu.jhu.pha.vospace.api.exceptions.NotFoundException ex) {
			throw new NotFoundException(identifier.getNodePath().getNodeStoragePath());
		}
		JobDescription job = new JobDescription();

		try {
			job.setTarget(node.getUri().toString());
		} catch (URISyntaxException e) {
			throw new BadRequestException("InvalidURI");
		}

		job.setDirection(DIRECTION.PULLFROMVOSPACE);
		job.setId(UUID.randomUUID().toString());
		job.setStartTime(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime());
		job.setState(JobDescription.STATE.PENDING);
		job.addProtocol("ivo://ivoa.net/vospace/core#httpget", null);
		job.setUsername(username);
		
		
		Method submitJobMethod;
		try {
			submitJobMethod = JobsProcessor.getImplClass().getMethod("submitJob", String.class, JobDescription.class);
			submitJobMethod.invoke(null, username, job);
			
	    	ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			JsonGenerator g2 = f.createJsonGenerator(byteOut).useDefaultPrettyPrinter();
	
			g2.writeStartObject();
			
			g2.writeStringField("url", conf.getString("application.url")+"/data/"+job.getId());
			g2.writeStringField("expires", "never (yet)");
			
			g2.writeEndObject();
			
			g2.close();
			byteOut.close();
		
			return Response.ok(byteOut.toByteArray()).build();
		} catch (Exception e) {
			throw new InternalServerErrorException(e.getMessage());
		}
	}
}

