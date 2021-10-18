package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;


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
    int count = 0;
    AtomicBoolean foundGoal = new AtomicBoolean(false);

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
        predecessor = new ConcurrentHashMap<Integer, Integer>();
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
    
    /**
        Constructor used when forking that passes shared variables to the children
     */
    public ForkJoinSolver(Maze maze, int start, int forkafter, Set<Integer> visited, Map<Integer, Integer> pred, AtomicBoolean foundGoal)
    {
        super(maze);
        this.start = start;
        this.forkAfter = forkafter;
        this.visited = visited;
        this.predecessor = pred;
        this.foundGoal = foundGoal;
    }

    
    /**
        The search algorithm that gets called recursivly by forking at certain points. 
        @return A list of steps that is the found path to the goal or null if no path was found.
     */
    private List<Integer> parallelSearch()
    { 
        // each fork has its own frontier, no need to share a thread safe one
        Deque<Integer> cFrontier = new ArrayDeque<>();

        // to avoid spawning a new player to a place where
        // a new player has already spawned
        int player = 0;
        if(!visited.contains(start)){ 
            cFrontier.push(start);
            // new player when starting new fork
            player = maze.newPlayer(start); 
        }
        
        // list to store forks in if this thread forks 
        List<ForkJoinSolver> forks = new ArrayList<>();
        
        // keep running until some fork finds the goal or this fork runs out of space to explore
        while(!foundGoal.get() && !cFrontier.isEmpty()){
            // get next node to explore
            int current = cFrontier.pop();
            
            // increment to keep count of where to fork   
            count += 1;
            
            // visit the next node
            if(visited.add(current)){
                // move this forks player icon to the next node
                maze.move(player, current);
                
                // checks if the next node is the goal node
                if(maze.hasGoal(current)){
                    // sets shared boolean to true
                    foundGoal.compareAndSet(false, true);
                    // returns path from start to current node 
                    return(pathFromTo(maze.start(), current));
                }

                // iterate over each of the current nodes' neighbors
                for (int nb: maze.neighbors(current)) {
                    // if nb has not been already visited,
                    // nb can be reached from current (i.e., current is nb's predecessor)
                    if (!visited.contains(nb)){             
                        predecessor.putIfAbsent(nb, current);
                    }
                    // if nb has been visited, do not create fork or push neighbour
                    if(visited.contains(nb)) continue;
                    
                    // creates new fork for neighbour (nb) if this node 
                    // has walked a certain amount of steps
                    // count     : counts the "steps"/iterations of while loop 
                    // forkAfter : decides the amount of steps 
                    if(count % forkAfter == 0){
                            ForkJoinSolver fork = new ForkJoinSolver(maze, nb, forkAfter, visited, predecessor, foundGoal);
                            forks.add(fork);
                            fork.fork();
                    } else        
                    // add nb to the nodes to be processed, 
                    // not including the nodes used as start nodes for new forks        
                        cFrontier.push(nb); 

                }                    
            }
            // joins all forks in the list of forks when they return a result
            for (ForkJoinSolver fork : forks){
                // stores the result in variable path
                List<Integer> path = fork.join();
                // Checks if result is not null,
                // if result is not null the result will be the path to the goal
                if(path != null){
                    return path;
                }   
            }  
        }   
        return null;
    }
}
