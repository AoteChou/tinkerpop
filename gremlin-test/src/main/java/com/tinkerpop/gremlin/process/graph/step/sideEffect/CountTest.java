package com.tinkerpop.gremlin.process.graph.step.sideEffect;

import com.tinkerpop.gremlin.AbstractGremlinTest;
import com.tinkerpop.gremlin.LoadGraphWith;
import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;

import static com.tinkerpop.gremlin.LoadGraphWith.GraphData.GRATEFUL;
import static com.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class CountTest extends AbstractGremlinTest {

    public abstract Traversal<Vertex, Long> get_g_V_count();

    public abstract Traversal<Vertex, Long> get_g_V_out_count();

    public abstract Traversal<Vertex, Long> get_g_V_both_both_count();

    public abstract Traversal<Vertex, Long> get_g_V_filterXfalseX_count();

    @Test
    @LoadGraphWith(MODERN)
    public void g_V_count() {
        final Traversal<Vertex, Long> traversal = get_g_V_count();
        printTraversalForm(traversal);
        assertEquals(new Long(6), traversal.next());
        assertFalse(traversal.hasNext());
    }

    @Test
    @LoadGraphWith(MODERN)
    public void g_V_out_count() {
        final Traversal<Vertex, Long> traversal = get_g_V_out_count();
        printTraversalForm(traversal);
        assertEquals(new Long(6), traversal.next());
        assertFalse(traversal.hasNext());
    }

    @Test
    @LoadGraphWith(GRATEFUL)
    public void g_V_both_both_count() {
        final Traversal<Vertex, Long> traversal = get_g_V_both_both_count();
        printTraversalForm(traversal);
        assertEquals(new Long(1406914), traversal.next());
        assertFalse(traversal.hasNext());
    }

    @Test
    @LoadGraphWith(MODERN)
    public void g_V_filterXfalseX_count() {
        final Traversal<Vertex, Long> traversal = get_g_V_filterXfalseX_count();
        printTraversalForm(traversal);
        assertEquals(new Long(0), traversal.next());
        assertFalse(traversal.hasNext());
    }

    public static class StandardTest extends CountTest {

        @Override
        public Traversal<Vertex, Long> get_g_V_count() {
            return g.V().count();
        }

        @Override
        public Traversal<Vertex, Long> get_g_V_out_count() {
            return g.V().out().count();
        }

        @Override
        public Traversal<Vertex, Long> get_g_V_both_both_count() {
            return g.V().both().both().count();
        }

        @Override
        public Traversal<Vertex, Long> get_g_V_filterXfalseX_count() {
            return g.V().filter(v -> false).count();
        }
    }

    public static class ComputerTest extends CountTest {

        @Override
        public Traversal<Vertex, Long> get_g_V_count() {
            return g.V().count().submit(g.compute());
        }

        @Override
        public Traversal<Vertex, Long> get_g_V_out_count() {
            return g.V().out().count().submit(g.compute());
        }

        @Override
        public Traversal<Vertex, Long> get_g_V_both_both_count() {
            return g.V().both().both().count().submit(g.compute());
        }

        @Override
        public Traversal<Vertex, Long> get_g_V_filterXfalseX_count() {
            return g.V().filter(v -> false).count().submit(g.compute());
        }
    }
}
