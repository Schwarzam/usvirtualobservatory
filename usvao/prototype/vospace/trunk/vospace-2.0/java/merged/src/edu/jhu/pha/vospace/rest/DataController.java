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
package edu.jhu.pha.vospace.rest;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

import javax.mail.internet.MimeUtility;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

import edu.jhu.pha.vospace.SettingsServlet;
import edu.jhu.pha.vospace.api.exceptions.InternalServerErrorException;
import edu.jhu.pha.vospace.api.exceptions.NotFoundException;
import edu.jhu.pha.vospace.api.exceptions.PermissionDeniedException;
import edu.jhu.pha.vospace.jobs.JobsProcessor;
import edu.jhu.pha.vospace.meta.MetaStore;
import edu.jhu.pha.vospace.meta.MetaStoreFactory;
import edu.jhu.pha.vospace.node.DataNode;
import edu.jhu.pha.vospace.node.Node;
import edu.jhu.pha.vospace.node.NodeFactory;
import edu.jhu.pha.vospace.node.NodeType;
import edu.jhu.pha.vospace.node.VospaceId;
import edu.jhu.pha.vospace.rest.JobDescription.STATE;
import edu.jhu.pha.vospace.storage.StorageManager;
import edu.jhu.pha.vospace.storage.StorageManagerFactory;
import edu.jhu.pha.vosync.exception.ForbiddenException;

/**
 * Provides the REST service for /data/ path: the functions for manipulating the nodes data content
 * @author Dmitry Mishin
 */
@Path("/data/")
public class DataController {
	
	private static final Logger logger = Logger.getLogger(DataController.class);
	private @Context HttpServletRequest request;
	private static Configuration conf = SettingsServlet.getConfig();
	
	/**
	 * Returns the data of a transfer
	 * @param jobId Job identifier
	 * @return transfer representation
	 */
	@GET @Path("{jobid}")
	public Response getTransferData(@PathParam("jobid") String jobId) {
		JobDescription job = JobsProcessor.getJob(UUID.fromString(jobId));
		if(null == job)
			throw new NotFoundException("The job "+jobId+" is not found.");

		
		VospaceId targetId=job.getTargetId();
		Node node = NodeFactory.getInstance().getNode(targetId, job.getUsername());
		
		if(job.getDirection().equals(JobDescription.DIRECTION.PULLFROMVOSPACE)){
			
			JobsProcessor.modifyJobState(job, STATE.RUN);
			
			logger.debug("Downloading node "+targetId.toString());
			
			Response.ResponseBuilder resp = Response.ok();
			
			try {
				InputStream dataInp = node.exportData();
				
				String fileName;
				String user_agent = request.getHeader("user-agent");
				boolean isInternetExplorer = (user_agent.indexOf("MSIE") > -1);
				if (isInternetExplorer) {
				    fileName = URLEncoder.encode(targetId.getNodePath().getNodeName(), "utf-8");
				} else {
					fileName = MimeUtility.encodeWord(targetId.getNodePath().getNodeName());
				}
				
				if(!node.getType().equals(NodeType.CONTAINER_NODE)) {
					resp.header("Content-Disposition", "attachment; filename=\""+fileName+"\"");
					resp.header("Content-Length", Long.toString(node.getNodeInfo().getSize()));
					resp.header("Content-Type", node.getNodeInfo().getContentType());
				} else {
					resp.header("Content-Disposition", "attachment; filename=\""+fileName+".tar\"");
					resp.header("Content-Type", "application/tar");
				}
				JobsProcessor.modifyJobState(job, STATE.COMPLETED);
				resp.entity(dataInp);
				return resp.build();
			} catch(InternalServerErrorException ex) {
				JobsProcessor.modifyJobState(job, STATE.ERROR);
				throw ex;
			} catch(NotFoundException ex) {
				JobsProcessor.modifyJobState(job, STATE.ERROR);
				throw ex;
			} catch(PermissionDeniedException ex) {
				JobsProcessor.modifyJobState(job, STATE.ERROR);
				throw ex;
			} catch (UnsupportedEncodingException ex) {
				JobsProcessor.modifyJobState(job, STATE.ERROR);
				throw new InternalServerErrorException(ex);
			}
		}
		
		throw new InternalServerErrorException("The job "+job.getDirection()+" is unsupported in this path.");
	}
	
	/**
	 * Method supporting data upload (push to VOSpace)
	 * @param fullPath The node path
	 * @param fileNameInp Node fileName
	 * @param fileDataInp Node data
	 * @return HTTP response
	 */
	@PUT @Path("{jobid}") 
    public Response uploadNodePut(@PathParam("jobid") String jobId, InputStream fileDataInp) {
		if(!(Boolean)request.getAttribute("write_permission")) {
			throw new ForbiddenException("ReadOnly");
		}
		
		JobDescription job = JobsProcessor.getJob(UUID.fromString(jobId));
		if(null == job)
			throw new NotFoundException("The job "+jobId+" is not found.");

		MetaStore store = MetaStoreFactory.getInstance().getMetaStore(job.getUsername());

		JobsProcessor.modifyJobState(job, STATE.RUN);
		
		if(job.getDirection().equals(JobDescription.DIRECTION.PUSHTOVOSPACE)){
			
			VospaceId id = job.getTargetId();

			if(!store.isStored(id)){
				Node node = NodeFactory.getInstance().createNode(id, job.getUsername(), NodeType.DATA_NODE);
				node.setNode(null);
			}
			
			logger.debug("Uploading node "+id);
			
			try {
				DataNode targetNode = (DataNode)NodeFactory.getInstance().getNode(id, job.getUsername());
				targetNode.setData(fileDataInp);
			} catch(InternalServerErrorException ex) {
				JobsProcessor.modifyJobState(job, STATE.ERROR);
				throw ex;
			}
			
			JobsProcessor.modifyJobState(job, STATE.COMPLETED);
			return Response.ok().build();
		}
		
		throw new InternalServerErrorException("The job "+job.getDirection()+" is unsupported in this path.");
    }
}
