package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;;


/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
    extends SequentialSolver
{
    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */
    public ForkJoinSolver(Maze maze)
    {
        super(maze);
        visited = new ConcurrentSkipListSet<>();
        cFrontier = new ConcurrentLinkedDeque<>();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        this(maze);
        this.forkAfter = forkAfter;
        
    }

    Deque<Integer> cFrontier;
    int count = 0;

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute()
    {
        return parallelSearch();
    }
    
    private List<Integer> parallelSearch()
    { 
        if(!visited.contains(start)){ 
            cFrontier.push(start);
            // new player when starting new fork
            int player = maze.newPlayer(start);
        }
  
        while(!cFrontier.isEmpty()){
            int current = cFrontier.pop();    
            
            if(maze.hasGoal(current)){
                maze.move(player, current);
                return pathFromTo(start, current);
            }

            if(!visited.contains(current)){
                maze.move(player, current);
                visited.add(current);
                List<ForkJoinSolver> forks = new ArrayList<>();
                count += 1;
                for (int nb: maze.neighbors(current)) {
                    // add nb to the nodes to be processed
                    cFrontier.push(nb);
                    // if nb has not been already visited,
                    // nb can be reached from current (i.e., current is nb's predecessor)
                    if (!visited.contains(nb)){                      
                        predecessor.put(nb, current);
                        if(count % 9 == 0){
                            
                            ForkJoinSolver fork = new ForkJoinSolver(maze);
                            forks.add(fork);
                            fork.fork(); 
                        }
                    }
                }
                for (ForkJoinSolver fork : forks){
                    List<Integer> path = fork.join();
                    if(path != null)
                        return path;
                }
                
            }
            
        }

        return null;
    }
}
