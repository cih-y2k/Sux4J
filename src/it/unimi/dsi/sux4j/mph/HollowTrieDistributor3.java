package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import static it.unimi.dsi.sux4j.mph.HypergraphSorter.GAMMA;
import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.bits.BalancedParentheses;
import it.unimi.dsi.sux4j.bits.JacobsonBalancedParentheses;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.util.LongBigList;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;

/** A distributor based on a hollow trie.
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>This class implements a distributor on top of a hollow trie. First, a compacted trie is built from the delimiter set.
 * Then, for each key we compute the node of the trie in which the bucket of the key is established. This gives us,
 * for each node of the trie, a set of paths to which we must associate an action (exit on the left,
 * go through, exit on the right). Overall, the number of such paths is equal to the number of keys plus the number of delimiters, so
 * the mapping from each pair node/path to the respective action takes linear space. Now, from the compacted trie we just
 * retain a hollow trie, as the path-length information is sufficient to rebuild the keys of the above mapping. 
 * By sizing the bucket size around the logarithm of the average length, we obtain a distributor that occupies linear space.
 */

public class HollowTrieDistributor3<T> extends AbstractObject2LongFunction<T> {
	private final static Logger LOGGER = Util.getLogger( HollowTrieDistributor3.class );
	private static final long serialVersionUID = 2L;
	private static final boolean DEBUG = false;
	private static final boolean DDEBUG = false;
	private static final boolean ASSERTS = false;

	/** An integer representing the exit-on-the-left behaviour. */
	private final static int LEFT = 0;
	/** An integer representing the exit-on-the-right behaviour. */
	private final static int RIGHT = 1;
	/** An integer representing the follow-the-try behaviour. */
	private final static int FOLLOW = 2;
	
	/** The transformation used to map object to bit vectors. */
	private final TransformationStrategy<? super T> transformationStrategy;
	/** The bitstream representing the hollow trie. */
	private final LongArrayBitVector trie;
	/** The list of skips, indexed by the internal nodes (we do not need skips on the leaves). */
	private final EliasFanoLongBigList skips;
	/** For each external node and each possible path, the related behaviour. */
	private final MWHCFunction<BitVector> externalBehaviour;
	/** The number of (internal and external) nodes of the trie. */
	private final int size;
	/** A debug function used to store explicitly {@link #externalBehaviour}. */
	private final Object2LongFunction<BitVector> externalTestFunction;
	/** A debug set used to store explicitly false follows. */
	private final ObjectOpenHashSet<BitVector> falseFollows;
	private final BalancedParentheses balParen;
	private final MWHCFunction<BitVector> falseFollowsDetector;
	/** The average skip. */
	protected double meanSkip;
	
	/** An intermediate class containing the compacted trie generated by the delimiters. After its construction,
	 * {@link #externalKeysFile} contains the pairs node/path that must be mapped
	 * to {@link #lValues}, respectively, to obtain the desired behaviour. */
	private final static class IntermediateTrie<T> {
		/** A debug function used to store explicitly the internal behaviour. */
		private Object2LongFunction<BitVector> externalTestFunction;
		/** A debug set used to store explicitly false follows. */
		private ObjectOpenHashSet<BitVector> falseFollows;
		/** The root of the trie. */
		protected final Node root;
		/** The number of overall elements to distribute. */
		private final int numElements;
		/** The number of internal nodes of the trie. */
		protected final int size;
		/** The file containing the external keys (pairs node/path). */
		private final File externalKeysFile;
		/** The values associated to the keys in {@link #externalKeysFile}. */
		private LongBigList externalValues;
		/** The file containing the keys (pairs node/path) that are (either true or false) follows. */
		private final File falseFollowsKeyFile;
		/** The values (true/false) associated to the keys in {@link #falseFollows}. */
		private LongBigList falseFollowsValues;
		
		/** A node in the trie. */
		public static class Node {
			/** Left child. */
			private Node left;
			/** Right child. */
			private Node right;
			/** The path compacted in this node (<code>null</code> if there is no compaction at this node). */
			private final LongArrayBitVector path;
			/** Whether we have already emitted the path at this node during the computation of the behaviour. */
			private boolean emitted;
			/** The index of this node in the Jacobson representation. */
			private int index;
			
			/** Creates a node. 
			 * 
			 * @param left the left child.
			 * @param right the right child.
			 * @param path the path compacted at this node.
			 */
			public Node( final Node left, final Node right, final LongArrayBitVector path ) {
				this.left = left;
				this.right = right;
				this.path = path;
			}

			/** Returns true if this node is a leaf.
			 * 
			 * @return true if this node is a leaf.
			 */
			public boolean isLeaf() {
				return right == null && left == null;
			}
			
			public String toString() {
				return "[" + path + "]";
			}
		}
			
		private int visit( final Node node, int index ) {
			if ( node == null ) return index;
			node.index = index++;
			index = visit( node.left, index );
			return visit( node.right, index ); // This adds the closing parenthesis
		}
		
		
		/** Creates a partial compacted trie using given elements, bucket size and transformation strategy.
		 * 
		 * @param elements the elements among which the trie must be able to rank.
		 * @param log2BucketSize the size of a bucket.
		 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
		 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
		 * @param tempDir a directory for the temporary files created during construction, or <code>null</code> for the default temporary directory. 
		 */
		
		public IntermediateTrie( final Iterable<? extends T> elements, final int log2BucketSize, final TransformationStrategy<? super T> transformationStrategy, final File tempDir ) throws IOException {
			if ( ASSERTS ) {
				externalTestFunction = new Object2LongOpenHashMap<BitVector>();
				externalTestFunction.defaultReturnValue( -1 );
				falseFollows = new ObjectOpenHashSet<BitVector>();
			}
			
			final int bucketSizeMask = ( 1 << log2BucketSize ) - 1;
			
			Iterator<? extends T> iterator = elements.iterator(); 

			if ( iterator.hasNext() ) {
				LongArrayBitVector prev = LongArrayBitVector.copy( transformationStrategy.toBitVector( iterator.next() ) );
				LongArrayBitVector prevDelimiter = LongArrayBitVector.getInstance();
				
				Node node, root = null;
				BitVector curr;
				int cmp, pos, prefix, count = 1;
				long maxLength = prev.length();
				
				while( iterator.hasNext() ) {
					// Check order
					curr = transformationStrategy.toBitVector( iterator.next() ).fast();
					cmp = prev.compareTo( curr );
					if ( cmp == 0 ) throw new IllegalArgumentException( "The input bit vectors are not distinct" );
					if ( cmp > 0 ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted" );
					if ( curr.longestCommonPrefixLength( prev ) == prev.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free" );

					if ( ( count & bucketSizeMask ) == 0 ) {
						// Found delimiter. Insert into trie.
						if ( root == null ) {
							root = new Node( null, null, prev.copy() );
							prevDelimiter.replace( prev );
						}
						else {
							prefix = (int)prev.longestCommonPrefixLength( prevDelimiter );

							pos = 0;
							node = root;
							Node n = null;
							while( node != null ) {
								final long pathLength = node.path.length();
								if ( prefix < pathLength ) {
									n = new Node( node.left, node.right, node.path.copy( prefix + 1, pathLength ) );
									node.path.length( prefix );
									node.path.trim();
									node.left = n;
									node.right = new Node( null, null, prev.copy( pos + prefix + 1, prev.length() ) ); 
									break;
								}

								prefix -= pathLength + 1;
								pos += pathLength + 1;
								node = node.right;
								if ( ASSERTS ) assert node == null || prefix >= 0 : prefix + " <= " + 0;
							}

							if ( ASSERTS ) assert node != null;

							prevDelimiter.replace( prev );
						}
					}
					prev.replace( curr );
					maxLength = Math.max( maxLength, prev.length() );
					count++;
				}

				this.numElements = count;
				this.root = root;
				
				externalKeysFile = File.createTempFile( HollowTrieDistributor3.class.getName(), "ext", tempDir );
				externalKeysFile.deleteOnExit();
				falseFollowsKeyFile = File.createTempFile( HollowTrieDistributor3.class.getName(), "false", tempDir );
				falseFollowsKeyFile.deleteOnExit();

				if ( root != null ) {
					LOGGER.info( "Numbering nodes..." );

					size = visit( root, 0 ); // Number nodes; we start from one so to add the fake root
					
					LOGGER.info( "Computing function keys..." );

					final OutputBitStream externalKeys = new OutputBitStream( externalKeysFile );
					final OutputBitStream falseFollowsKeys = new OutputBitStream( falseFollowsKeyFile );

					externalValues = LongArrayBitVector.getInstance().asLongBigList( 1 );
					falseFollowsValues = LongArrayBitVector.getInstance().asLongBigList( 1 );
					iterator = elements.iterator();

					// The stack of nodes visited the last time
					final Node stack[] = new Node[ (int)maxLength ];
					// The length of the path compacted in the trie up to the corresponding node, excluded
					final int[] len = new int[ (int)maxLength ];
					stack[ 0 ] = root;
					int depth = 0, behaviour, pathLength;
					boolean first = true;
					Node lastNode = null;
					BitVector currFromPos, path, lastPath = null;
					LongArrayBitVector nodePath;
					OutputBitStream obs;

					while( iterator.hasNext() ) {
						curr = transformationStrategy.toBitVector( iterator.next() ).fast();
						if ( DEBUG ) System.err.println( curr );
						if ( ! first )  {
							// Adjust stack using lcp between present string and previous one
							prefix = (int)prev.longestCommonPrefixLength( curr );
							while( depth > 0 && len[ depth ] > prefix ) depth--;
						}
						else first = false;
						node = stack[ depth ];
						pos = len[ depth ];

						for(;;) {
							nodePath = node.path;
							currFromPos = curr.subVector( pos ); 
							prefix = (int)currFromPos.longestCommonPrefixLength( nodePath );
							int falseFollow = -1;
							
							if ( prefix < nodePath.length() || ! node.emitted ) {
								// Either we must code an exit behaviour, or the follow behaviour of this node has not been coded yet.
								if ( prefix == nodePath.length() ) {
									behaviour = LEFT;
									path = nodePath;
									node.emitted = true;
									if ( ! node.isLeaf() ) {
										falseFollow = 0;
										behaviour = FOLLOW;
									}
									if ( ASSERTS ) assert ! node.isLeaf() || currFromPos.length() == nodePath.length();
								}
								else {
									// Exit. LEFT or RIGHT, depending on the bit at the end of the common prefix. The
									// path is the remaining path at the current position for external nodes, or a prefix of length
									// at most pathLength for internal nodes.
									behaviour = nodePath.getBoolean( prefix ) ? LEFT : RIGHT;
									path = node.isLeaf() ? currFromPos.copy() :	currFromPos.subVector( 0, Math.min( currFromPos.length(), nodePath.length() ) ).copy();
								}

								if ( behaviour != FOLLOW && ( lastNode != node || ! path.equals( lastPath ) ) ) {
									externalValues.add( behaviour );
									obs = externalKeys;

									pathLength = (int)path.length();

									obs.writeLong( node.index, Long.SIZE );
									obs.writeDelta( pathLength );
									for( int i = 0; i < pathLength; i += Long.SIZE ) obs.writeLong( path.getLong( i, Math.min( i + Long.SIZE, pathLength) ), Math.min( Long.SIZE, pathLength - i ) );

									lastNode = node;
									lastPath = path;
									
									if ( ! node.isLeaf() ) falseFollow = 1;

									if ( ASSERTS ) {
										long key[] = new long[ ( pathLength + Long.SIZE - 1 ) / Long.SIZE + 1 ];
										key[ 0 ] = node.index;
										for( int i = 0; i < pathLength; i += Long.SIZE ) key[ i / Long.SIZE + 1 ] = path.getLong( i, Math.min( i + Long.SIZE, pathLength ) );
										externalTestFunction.put( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ), behaviour );
										if ( ! node.isLeaf() ) 
											falseFollows.add( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ) );
									}

									if ( DEBUG ) {
										System.err.println( "Computed " + ( node.isLeaf() ? "leaf " : "" ) + "mapping <" + node.index + ", [" + path.length() + ", " + Integer.toHexString( path.hashCode() ) + "] " + path + "> -> " + behaviour );
										System.err.println( externalTestFunction );
									}

								}
								
								if ( falseFollow != -1 ) {
									falseFollowsValues.add( falseFollow );
									pathLength = (int)path.length();
									falseFollowsKeys.writeLong( node.index, Long.SIZE );
									falseFollowsKeys.writeDelta( pathLength );
									for( int i = 0; i < pathLength; i += Long.SIZE ) falseFollowsKeys.writeLong( path.getLong( i, Math.min( i + Long.SIZE, pathLength) ), Math.min( Long.SIZE, pathLength - i ) );
								}
								
								if ( behaviour != FOLLOW ) break;

							}

							pos += nodePath.length() + 1;
							if ( pos > curr.length() ) break;
							node = curr.getBoolean( pos - 1 ) ? node.right : node.left;
							// Update stack
							len[ ++depth ] = pos;
							stack[ depth ] = node;
						}

						prev.replace( curr );
					}

					externalKeys.close();
					falseFollowsKeys.close();
				}
				else size = 0;
			}
			else {
				// No elements.
				this.root = null;
				this.size = this.numElements = 0;
				falseFollowsKeyFile = externalKeysFile = null;
			}
		}

		private void recToString( final Node n, final MutableString printPrefix, final MutableString result, final MutableString path, final int level ) {
			if ( n == null ) return;
			
			result.append( printPrefix ).append( '(' ).append( level ).append( ')' ).append( " [" ).append( n.index ).append( ']' );
			
			if ( n.path != null ) {
				path.append( n.path );
				result.append( " path: " ).append( "[" ).append( path.length() ).append( ", " ).append( Integer.toHexString( path.hashCode() ) ).append( "] " ).append( n.path );
			}

			result.append( '\n' );
			
			path.append( '0' );
			recToString( n.left, printPrefix.append( '\t' ).append( "0 => " ), result, path, level + 1 );
			path.charAt( path.length() - 1, '1' ); 
			recToString( n.right, printPrefix.replace( printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1 );
			path.delete( path.length() - 1, path.length() ); 
			printPrefix.delete( printPrefix.length() - 6, printPrefix.length() );
			
			path.delete( (int)( path.length() - n.path.length() ), path.length() );
		}
		
		public String toString() {
			MutableString s = new MutableString();
			recToString( root, new MutableString(), s, new MutableString(), 0 );
			return s.toString();
		}

	}
	
	private long visit( final IntermediateTrie.Node node, LongArrayBitVector bitVector, long pos, IntArrayList skips ) {
		if ( node.isLeaf() ) return pos;

		bitVector.set( pos++ ); // This adds the open parentheses
		
		skips.add( (int)node.path.length() );
		
		pos = visit( node.left, bitVector, pos, skips );

		return visit( node.right, bitVector, pos + 1, skips ); // This adds the closing parenthesis
	}
	
	/** Creates a partial compacted trie using given elements, bucket size and transformation strategy.
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param log2BucketSize the logarithm of the size of a bucket.
	 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, lexicographically increasing (in iteration order) bit vectors.
	 */
	public HollowTrieDistributor3( final Iterable<? extends T> elements, final int log2BucketSize, final TransformationStrategy<? super T> transformationStrategy ) throws IOException {
		this( elements, log2BucketSize, transformationStrategy, null );
	}

	/** Creates a partial compacted trie using given elements, bucket size, transformation strategy, and temporary directory.
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param log2BucketSize the logarithm of the size of a bucket.
	 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, lexicographically increasing (in iteration order) bit vectors.
	 * @param tempDir the directory where temporary files will be created, or <code>for the default directory</code>.
	 */
	public HollowTrieDistributor3( final Iterable<? extends T> elements, final int log2BucketSize, final TransformationStrategy<? super T> transformationStrategy, final File tempDir ) throws IOException {
		this.transformationStrategy = transformationStrategy;
		final int bucketSize = 1 << log2BucketSize;
		if ( DEBUG ) System.err.println( "Bucket size: " + bucketSize );
		final IntermediateTrie<T> intermediateTrie = new IntermediateTrie<T>( elements, log2BucketSize, transformationStrategy, tempDir );

		size = intermediateTrie.size;
		externalTestFunction = intermediateTrie.externalTestFunction;
		falseFollows = intermediateTrie.falseFollows;
		
		trie = LongArrayBitVector.ofLength( size + 1 );
		trie.set( 0 );
		IntArrayList skips = new IntArrayList();
		// Turn the compacted trie into a hollow trie.
		if ( intermediateTrie.root != null ) {
			if ( DDEBUG ) System.err.println( intermediateTrie );
			visit( intermediateTrie.root, trie, 1, skips );
		}
		
		balParen = new JacobsonBalancedParentheses( trie, false, true, false );
		
		long mean = 0;
		for( int s : skips ) mean += s;
		meanSkip = (double)mean / skips.size();
		
		this.skips = new EliasFanoLongBigList( skips );
		skips = null;
		
		LOGGER.info( "Bits per skip: " + bitsPerSkip() );

		/** A class iterating over the temporary files produced by the intermediate trie. */
		class IterableStream implements Iterable<BitVector> {
			private InputBitStream ibs;
			private int n;
			private Object2LongFunction<BitVector> test;
			private LongBigList values;
			
			public IterableStream( final InputBitStream ibs, final Object2LongFunction<BitVector> testFunction, final LongBigList testValues ) {
				this.ibs = ibs;
				this.n = testValues.size();
				this.test = testFunction;
				this.values = testValues;
			}

			public Iterator<BitVector> iterator() {
				try {
					ibs.position( 0 );
					return new AbstractObjectIterator<BitVector>() {
						private int pos = 0;
						
						public boolean hasNext() {
							return pos < n;
						}

						public BitVector next() {
							if ( ! hasNext() ) throw new NoSuchElementException();
							try {
								final long index = ibs.readLong( 64 );
								final int pathLength = ibs.readDelta();
								final long key[] = new long[ ( ( pathLength + Long.SIZE - 1 ) / Long.SIZE + 1 ) ];
								key[ 0 ] = index;
								for( int i = 0; i < ( pathLength + Long.SIZE - 1 ) / Long.SIZE; i++ ) key[ i + 1 ] = ibs.readLong( Math.min( Long.SIZE, pathLength - i * Long.SIZE ) );
								
								if ( DEBUG ) {
									System.err.println( "Adding mapping <" + index + ", " +  LongArrayBitVector.wrap( key, pathLength + Long.SIZE ).subVector( Long.SIZE ) + "> -> " + values.getLong( pos ));
									System.err.println(  LongArrayBitVector.wrap( key, pathLength + Long.SIZE ) );
								}

								if ( ASSERTS && test != null ) assert test.getLong( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ) ) == values.getLong( pos ) : test.getLong( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ) ) + " != " + values.getLong( pos ) ;
								
								pos++;
								return LongArrayBitVector.wrap( key, pathLength + Long.SIZE );
							}
							catch ( IOException e ) {
								throw new RuntimeException( e );
							}
						}
					};
				}
				catch ( IOException e ) {
					throw new RuntimeException( e );
				}
			}
		};
		
		externalBehaviour = new MWHCFunction<BitVector>( new IterableStream( new InputBitStream( intermediateTrie.externalKeysFile ), externalTestFunction, intermediateTrie.externalValues ), TransformationStrategies.identity(), intermediateTrie.externalValues, 1 );
		falseFollowsDetector = new MWHCFunction<BitVector>( new IterableStream( new InputBitStream( intermediateTrie.falseFollowsKeyFile ), null, intermediateTrie.falseFollowsValues ), TransformationStrategies.identity(), intermediateTrie.falseFollowsValues, 1 );
		
		LOGGER.debug( "False positives: " + ( falseFollowsDetector.size() - size / 2 ) );
		
		intermediateTrie.externalKeysFile.delete();

		if ( ASSERTS ) {
			if ( size > 0 ) {
				Iterator<BitVector>iterator = TransformationStrategies.wrap( elements.iterator(), transformationStrategy );
				int c = 0;
				while( iterator.hasNext() ) {
					BitVector curr = iterator.next();
					if ( DEBUG ) System.err.println( "Checking element number " + c + ( ( c + 1 ) % bucketSize == 0 ? " (bucket)" : "" ));
					long t = getLong( curr );
					assert c / bucketSize == t : c / bucketSize + " != " + t;
					c++;
				}		
			}
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( size == 0 ) return 0;
		final BitVector bitVector = transformationStrategy.toBitVector( (T)o ).fast();
		LongArrayBitVector key = LongArrayBitVector.getInstance();
		BitVector fragment = null;
		long p = 1, length = bitVector.length(), index = 0, r = 0;
		int s = 0, skip = 0, behaviour;
		long lastLeftTurn = 0;
		long lastLeftTurnIndex = 0;
		boolean isInternal;
			
		if ( DEBUG ) System.err.println( "Distributing " + bitVector + "\ntrie:" + trie );
		
		for(;;) {
			isInternal = trie.getBoolean( p );
			if ( isInternal ) skip = (int)skips.getLong( r );
			if ( DEBUG ) System.err.println( "Interrogating" + ( isInternal ? "" : " leaf" ) + " <" + ( p - 1 ) + ", [" + Math.min( length, s + skip ) + ", " + Integer.toHexString( bitVector.subVector( s, Math.min( length, s + skip ) ).hashCode() ) + "] " + bitVector.subVector( s, Math.min( length, s + skip ) ) + "> (skip: " + skip + ")" );

			//if ( isInternal ) System.err.println( signature == ( Hashes.jenkins( bitVector.subVector( s, Math.min( length, s + skip ) ) ) & ( 1 << SKIPBITS )- 1 ) );
			

			if ( isInternal && falseFollowsDetector.getLong( key.length( 0 ).append( p - 1, Long.SIZE ).append( fragment = bitVector.subVector( s, Math.min( length, s + skip ) ) ) ) == 0 ) behaviour = FOLLOW;
			else behaviour = (int)externalBehaviour.getLong( key.length( 0 ).append( p - 1, Long.SIZE ).append( isInternal ? fragment : bitVector.subVector( s, length ) ) );
			
			if ( ASSERTS ) {
				if ( behaviour != FOLLOW ) {
					final long result; 
					result = externalTestFunction.getLong( key.length( 0 ).append( p - 1, Long.SIZE ).append( bitVector.subVector( s, isInternal ? Math.min( length, s + skip ) : length ) ) ); 
					//assert result != -1; // Only if you don't test with non-keys
					if ( result != -1 ) assert result == behaviour : result + " != " + behaviour;
				}
				else assert ! falseFollows.contains( key.length( 0 ).append( p - 1, Long.SIZE ).append( bitVector.subVector( s, Math.min( length, s + skip ) ) ) );
			}
			
			//if ( ASSERTS ) assert behaviour == LEFT || behaviour == RIGHT || behaviour == FOLLOW : behaviour; // Only if you don't test with non-keys
			if ( DEBUG ) System.err.println( "Exit behaviour: " + behaviour );

			if ( behaviour != FOLLOW || ! isInternal || ( s += skip ) >= length ) break;

			if ( DEBUG ) System.err.print( "Turning " + ( bitVector.getBoolean( s ) ? "right" : "left" ) + " at bit " + s + "... " );
			
			if ( bitVector.getBoolean( s ) ) {
				final long q = balParen.findClose( p ) + 1;
				index += ( q - p ) / 2;
				r += ( q - p ) / 2;
				//System.err.println( "Increasing index by " + ( q - p + 1 ) / 2 + " to " + index + "..." );
				p = q;
			}
			else {
				lastLeftTurn = p;
				lastLeftTurnIndex = index;
				p++;
				r++;
			}
			
			if ( ASSERTS ) assert p < trie.length();

			s++;
		}
		
		if ( behaviour == LEFT ) {
			if ( DEBUG ) System.err.println( "Returning (on the left) " + index );
			return index;	
		}
		else {
			if ( isInternal ) {
				final long q = balParen.findClose( lastLeftTurn );
				//System.err.println( p + ", " + q + " ," + lastLeftTurn + ", " +lastLeftTurnIndex);;
				index = ( q - lastLeftTurn + 1 ) / 2 + lastLeftTurnIndex;			
				if ( DEBUG ) System.err.println( "Returning (on the right, internal) " + index );
			}
			else {
				index++;
				if ( DEBUG ) System.err.println( "Returning (on the right, external) " + index );
			}
			return index;	
		}
		
	}
	
	public long numBits() {
		return trie.length() + skips.numBits() + falseFollowsDetector.numBits() + balParen.numBits() + externalBehaviour.numBits() + transformationStrategy.numBits();
	}
	
	public boolean containsKey( Object o ) {
		return true;
	}

	public int size() {
		return size;
	}
	
	public double bitsPerSkip() {
		return (double)skips.numBits() / skips.length();
	}
}
