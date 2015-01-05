/*
 * Hivemall: Hive scalable Machine Learning Library
 *
 * Copyright (C) 2013-2014
 *   National Institute of Advanced Industrial Science and Technology (AIST)
 *   Registration Number: H25PRO-1520
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package hivemall.io;

import hivemall.common.RatingInitilizer;
import hivemall.utils.collections.IntOpenHashMap;
import hivemall.utils.math.MathUtils;

import java.util.Random;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public final class FactorizedModel {

    @Nonnull
    private final RatingInitilizer ratingInitializer;
    @Nonnegative
    private final int factor;

    // rank matrix initialization
    private final boolean randInit;
    @Nonnegative
    private final float maxInitValue;
    @Nonnegative
    private final double initStdDev;

    private int minIndex, maxIndex;
    @Nonnull
    private Rating meanRating;
    private IntOpenHashMap<Rating[]> users;
    private IntOpenHashMap<Rating[]> items;
    private IntOpenHashMap<Rating> userBias;
    private IntOpenHashMap<Rating> itemBias;

    private final Random randU, randI;

    public FactorizedModel(@Nonnull RatingInitilizer ratingInitializer, @Nonnegative int factor, float meanRating, boolean randInit, @Nonnegative float maxInitValue, @Nonnegative double initStdDev) {
        this(ratingInitializer, factor, meanRating, randInit, maxInitValue, initStdDev, 136861);
    }

    public FactorizedModel(@Nonnull RatingInitilizer ratingInitializer, @Nonnegative int factor, float meanRating, boolean randInit, @Nonnegative float maxInitValue, @Nonnegative double initStdDev, int expectedSize) {
        this.ratingInitializer = ratingInitializer;
        this.factor = factor;
        this.randInit = randInit;
        this.maxInitValue = maxInitValue;
        this.initStdDev = initStdDev;
        this.minIndex = 0;
        this.maxIndex = 0;
        this.meanRating = ratingInitializer.newRating(meanRating);
        this.users = new IntOpenHashMap<Rating[]>(expectedSize);
        this.items = new IntOpenHashMap<Rating[]>(expectedSize);
        this.userBias = new IntOpenHashMap<Rating>(expectedSize);
        this.itemBias = new IntOpenHashMap<Rating>(expectedSize);
        this.randU = new Random(31L);
        this.randI = new Random(41L);
    }

    public int getMinIndex() {
        return minIndex;
    }

    public int getMaxIndex() {
        return maxIndex;
    }

    @Nonnull
    public Rating meanRating() {
        return meanRating;
    }

    public float getMeanRating() {
        return meanRating.getWeight();
    }

    public void setMeanRating(float rating) {
        meanRating.setWeight(rating);
    }

    @Nullable
    public Rating[] getUserVector(int u) {
        return getUserVector(u, false);
    }

    @Nullable
    public Rating[] getUserVector(int u, boolean init) {
        Rating[] v = users.get(u);
        if(init && v == null) {
            v = new Rating[factor];
            if(randInit) {
                uniformFill(v, randU, maxInitValue, ratingInitializer);
            } else {
                gaussianFill(v, randU, initStdDev, ratingInitializer);
            }
            users.put(u, v);
            this.maxIndex = Math.max(maxIndex, u);
            this.minIndex = Math.min(minIndex, u);
        }
        return v;
    }

    @Nullable
    public Rating[] getItemVector(int i) {
        return getItemVector(i, false);
    }

    @Nullable
    public Rating[] getItemVector(int i, boolean init) {
        Rating[] v = items.get(i);
        if(init && v == null) {
            v = new Rating[factor];
            if(randInit) {
                uniformFill(v, randI, maxInitValue, ratingInitializer);
            } else {
                gaussianFill(v, randI, initStdDev, ratingInitializer);
            }
            items.put(i, v);
            this.maxIndex = Math.max(maxIndex, i);
            this.minIndex = Math.min(minIndex, i);
        }
        return v;
    }

    @Nonnull
    public Rating userBias(int u) {
        Rating b = userBias.get(u);
        if(b == null) {
            b = ratingInitializer.newRating(0.f); // dummy
            userBias.put(u, b);
        }
        return b;
    }

    public float getUserBias(int u) {
        Rating b = userBias.get(u);
        if(b == null) {
            return 0.f;
        }
        return b.getWeight();
    }

    public void setUserBias(int u, float value) {
        Rating b = userBias.get(u);
        if(b == null) {
            b = ratingInitializer.newRating(value);
            userBias.put(u, b);
        }
        b.setWeight(value);
    }

    @Nonnull
    public Rating itemBias(int i) {
        Rating b = itemBias.get(i);
        if(b == null) {
            b = ratingInitializer.newRating(0.f); // dummy
            itemBias.put(i, b);
        }
        return b;
    }

    @Nullable
    public Rating getItemBiasObject(int i) {
        return itemBias.get(i);
    }

    public float getItemBias(int i) {
        Rating b = itemBias.get(i);
        if(b == null) {
            return 0.f;
        }
        return b.getWeight();
    }

    public void setItemBias(int i, float value) {
        Rating b = itemBias.get(i);
        if(b == null) {
            b = ratingInitializer.newRating(value);
            itemBias.put(i, b);
        }
        b.setWeight(value);
    }

    private static void uniformFill(final Rating[] a, final Random rand, final float maxInitValue, final RatingInitilizer init) {
        for(int i = 0, len = a.length; i < len; i++) {
            float v = rand.nextFloat() * maxInitValue / len;
            a[i] = init.newRating(v);
        }
    }

    private static void gaussianFill(final Rating[] a, final Random rand, final double stddev, final RatingInitilizer init) {
        for(int i = 0, len = a.length; i < len; i++) {
            float v = (float) MathUtils.gaussian(0.d, stddev, rand);
            a[i] = init.newRating(v);
        }
    }

}