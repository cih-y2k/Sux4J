package it.unimi.dsi.sux4j.util;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.util.ZFastTrie;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class ZFastTrieTest extends TestCase {


	public static String binary( int l ) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@SuppressWarnings("unchecked")
	public void testEmpty() throws IOException, ClassNotFoundException {
		String[] s = {};
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		assertFalse( zft.contains( "" ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		assertFalse( zft.contains( "" ) );
	}

	@SuppressWarnings("unchecked")
	public void testSingleton() throws IOException, ClassNotFoundException {
		String[] s = { "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testDoubleton() throws IOException, ClassNotFoundException {
		String[] s = { "a", "b" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testDoubleton2() throws IOException, ClassNotFoundException {
		String[] s = { "b", "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testTriple() throws IOException, ClassNotFoundException {
		String[] s = { "a", "b", "c" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testTriple2() throws IOException, ClassNotFoundException {
		String[] s = { "c", "b", "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testSmallest() throws IOException, ClassNotFoundException {
		String[] s = { "a", "b", "c", "d", "e", "f", "g" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
		assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testSmallest2() throws IOException, ClassNotFoundException {
		String[] s = { "g", "f", "e", "d", "c", "b", "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
		assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testSmall() throws IOException, ClassNotFoundException {
		String[] s = { "-", "0", "1", "4", "5", "a", "b", "c", "d", "e", "f", "g", "}" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for ( int d = 10; d < 10000; d *= 10 ) {
			String[] s = new String[ d ];
			int[] v = new int[ s.length ];
			for ( int i = s.length; i-- != 0; )
				s[ v[ i ] = i ] = binary( i );

			ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
			zft.numBits();

			for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

			// Exercise code for negative results
			for ( int i = 1000; i-- != 0; )
				zft.contains( binary( i * i + d ) );

			File temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( zft, temp );
			zft = (ZFastTrie<String>)BinIO.loadObject( temp );
			for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

			zft = new ZFastTrie<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ), true ) );
			zft.numBits();

			for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

			temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( zft, temp );
			zft = (ZFastTrie<String>)BinIO.loadObject( temp );
			for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );
		}
	}
}