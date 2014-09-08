/*******************************************************************************
 * Copyright (c) 2012, Johns Hopkins University
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
package edu.jhu.pha.vospace.meta;

import java.util.Date;
import java.util.List;
import java.util.Map;

import edu.jhu.pha.vospace.node.Node;
import edu.jhu.pha.vospace.node.NodeInfo;
import edu.jhu.pha.vospace.node.NodeType;
import edu.jhu.pha.vospace.node.VospaceId;

/**
 * This interface represents a metadata store for VOSpace 
 */
public interface MetaStoreDistributed {


    /**
     * Returns the list of regions storing the container
     * @param uri The container URI
     * @return List of container locations
     */
    public List<String> getNodeRegions(VospaceId uri);

    public RegionsInfo getRegionsInfo();

	public void setNodeRegions(VospaceId identifier, Map<String, String> regions);
}