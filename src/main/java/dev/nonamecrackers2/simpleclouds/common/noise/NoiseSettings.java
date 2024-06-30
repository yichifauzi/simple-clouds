package dev.nonamecrackers2.simpleclouds.common.noise;

import java.util.function.Function;

import com.mojang.serialization.Decoder;
import com.mojang.serialization.codecs.EitherCodec;

public interface NoiseSettings
{
	public static final NoiseSettings EMPTY = new NoiseSettings()
	{
		@Override
		public float[] packForShader()
		{
			return new float[0];
		}
		
		@Override
		public int layerCount()
		{
			return 0;
		}
	};
	public static Decoder<NoiseSettings> STATIC = new EitherCodec<>(StaticNoiseSettings.CODEC, StaticLayeredNoise.CODEC).map(either -> {
		return either.map(Function.identity(), Function.identity());
	});
	public static Decoder<NoiseSettings> MODIFIABLE = new EitherCodec<>(ModifiableNoiseSettings.CODEC, ModifiableLayeredNoise.CODEC).map(either -> {
		return either.map(Function.identity(), Function.identity());
	});
	
	float[] packForShader();
	
	int layerCount();
}
