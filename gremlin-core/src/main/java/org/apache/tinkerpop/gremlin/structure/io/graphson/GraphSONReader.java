/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.structure.io.graphson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.batch.BatchGraph;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedEdge;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedProperty;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedVertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraph;
import org.apache.tinkerpop.gremlin.structure.util.star.StarGraphGraphSONSerializer;
import org.apache.tinkerpop.gremlin.util.function.FunctionUtils;
import org.javatuples.Pair;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A @{link GraphReader} that constructs a graph from a JSON-based representation of a graph and its elements.
 * This implementation only supports JSON data types and is therefore lossy with respect to data types (e.g. a
 * float will become a double, element IDs may not be retrieved in the format they were serialized, etc.).
 * {@link Edge} and {@link Vertex} objects are serialized to {@code Map} instances.  If an
 * {@link org.apache.tinkerpop.gremlin.structure.Element} is used as a key, it is coerced to its identifier.  Other complex
 * objects are converted via {@link Object#toString()} unless there is a mapper serializer supplied.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class GraphSONReader implements GraphReader {
    private final ObjectMapper mapper;
    private final long batchSize;

    final TypeReference<Map<String, Object>> mapTypeReference = new TypeReference<Map<String, Object>>() {
    };

    public GraphSONReader(final GraphSONMapper mapper, final long batchSize) {
        this.mapper = mapper.createMapper();
        this.batchSize = batchSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readGraph(final InputStream inputStream, final Graph graphToWriteTo) throws IOException {
        // dual pass - create all vertices and store to cache the ids.  then create edges.  as long as we don't
        // have vertex labels in the output we can't do this single pass
        final Map<StarGraph.StarVertex,Vertex> cache = new HashMap<>();
        final AtomicLong counter = new AtomicLong(0);
        final boolean supportsTx = graphToWriteTo.features().graph().supportsTransactions();
        final BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        br.lines().<Vertex>map(FunctionUtils.wrapFunction(line -> readVertex(new ByteArrayInputStream(line.getBytes()), null, null, Direction.OUT))).forEach(vertex -> {
            final Attachable<Vertex> attachable = (Attachable<Vertex>) vertex;
            cache.put((StarGraph.StarVertex) attachable.get(), attachable.attach(Attachable.Method.create(graphToWriteTo)));
            if (supportsTx && counter.incrementAndGet() % batchSize == 0)
                graphToWriteTo.tx().commit();
        });
        cache.entrySet().forEach(kv -> kv.getKey().edges(Direction.OUT).forEachRemaining(e -> {
            ((StarGraph.StarEdge) e).attach(Attachable.Method.create(kv.getValue()));
            if (supportsTx && counter.incrementAndGet() % batchSize == 0)
                graphToWriteTo.tx().commit();
        }));

        if (supportsTx) graphToWriteTo.tx().commit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Vertex> readVertices(final InputStream inputStream,
                                         final Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
                                         final Function<Attachable<Edge>, Edge> edgeAttachMethod,
                                         final Direction attachEdgesOfThisDirection) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        return br.lines().<Vertex>map(FunctionUtils.wrapFunction(line -> readVertex(new ByteArrayInputStream(line.getBytes()), vertexAttachMethod, edgeAttachMethod, attachEdgesOfThisDirection))).iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Vertex readVertex(final InputStream inputStream, final Function<Attachable<Vertex>, Vertex> vertexAttachMethod) throws IOException {
        return readVertex(inputStream, vertexAttachMethod, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Vertex readVertex(final InputStream inputStream,
                             final Function<Attachable<Vertex>, Vertex> vertexAttachMethod,
                             final Function<Attachable<Edge>, Edge> edgeAttachMethod,
                             final Direction attachEdgesOfThisDirection) throws IOException {
        final Map<String, Object> vertexData = mapper.readValue(inputStream, mapTypeReference);
        final StarGraph starGraph = StarGraphGraphSONSerializer.readStarGraphVertex(vertexData);
        if (vertexAttachMethod != null) vertexAttachMethod.apply(starGraph.getStarVertex());

        if (vertexData.containsKey(GraphSONTokens.OUT_E) && (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.OUT))
            StarGraphGraphSONSerializer.readStarGraphEdges(edgeAttachMethod, starGraph, vertexData, GraphSONTokens.OUT_E);

        if (vertexData.containsKey(GraphSONTokens.IN_E) && (attachEdgesOfThisDirection == Direction.BOTH || attachEdgesOfThisDirection == Direction.IN))
            StarGraphGraphSONSerializer.readStarGraphEdges(edgeAttachMethod, starGraph, vertexData, GraphSONTokens.IN_E);

        return starGraph.getStarVertex();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Edge readEdge(final InputStream inputStream, final Function<Attachable<Edge>, Edge> edgeAttachMethod) throws IOException {
        final Map<String, Object> edgeData = mapper.readValue(inputStream, mapTypeReference);

        final Map<String,Object> edgeProperties = edgeData.containsKey(GraphSONTokens.PROPERTIES) ?
                (Map<String, Object>) edgeData.get(GraphSONTokens.PROPERTIES) : Collections.EMPTY_MAP;
        final DetachedEdge edge = new DetachedEdge(edgeData.get(GraphSONTokens.ID),
                edgeData.get(GraphSONTokens.LABEL).toString(),
                edgeProperties,
                Pair.with(edgeData.get(GraphSONTokens.OUT), edgeData.get(GraphSONTokens.OUT_LABEL).toString()),
                Pair.with(edgeData.get(GraphSONTokens.IN), edgeData.get(GraphSONTokens.IN_LABEL).toString()));

        return edgeAttachMethod.apply(edge);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VertexProperty readVertexProperty(final InputStream inputStream,
                                             final Function<Attachable<VertexProperty>, VertexProperty> vertexPropertyAttachMethod) throws IOException {
        final Map<String, Object> vpData = mapper.readValue(inputStream, mapTypeReference);
        final Map<String, Object> metaProperties = (Map<String, Object>) vpData.get(GraphSONTokens.PROPERTIES);
        final DetachedVertexProperty vp = new DetachedVertexProperty(vpData.get(GraphSONTokens.ID),
                vpData.get(GraphSONTokens.LABEL).toString(),
                vpData.get(GraphSONTokens.VALUE), metaProperties);
        return vertexPropertyAttachMethod.apply(vp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Property readProperty(final InputStream inputStream,
                                 final Function<Attachable<Property>, Property> propertyAttachMethod) throws IOException {
        final Map<String, Object> propertyData = mapper.readValue(inputStream, mapTypeReference);
        final DetachedProperty p = new DetachedProperty(propertyData.get(GraphSONTokens.KEY).toString(), propertyData.get(GraphSONTokens.VALUE));
        return propertyAttachMethod.apply(p);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C> C readObject(final InputStream inputStream, final Class<? extends C> clazz) throws IOException {
        return mapper.readValue(inputStream, clazz);
    }

    public static Builder build() {
        return new Builder();
    }

    public static class Builder implements ReaderBuilder<GraphSONReader> {
        private long batchSize = BatchGraph.DEFAULT_BUFFER_SIZE;

        private GraphSONMapper mapper = GraphSONMapper.build().create();

        private Builder() {
        }

        /**
         * Number of mutations to perform before a commit is executed.
         */
        public Builder batchSize(final long batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Override all of the {@link GraphSONMapper} builder
         * options with this mapper.  If this value is set to something other than null then that value will be
         * used to construct the writer.
         */
        public Builder mapper(final GraphSONMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public GraphSONReader create() {
            return new GraphSONReader(mapper, batchSize);
        }
    }
}
