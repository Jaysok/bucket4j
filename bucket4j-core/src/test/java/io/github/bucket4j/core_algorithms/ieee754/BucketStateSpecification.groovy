/*
 *
 * Copyright 2015-2018 Vladimir Bukhtoyarov
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package io.github.bucket4j.core_algorithms.ieee754

import io.github.bucket4j.*
import io.github.bucket4j.local.LocalBucket
import io.github.bucket4j.mock.TimeMeterMock
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class BucketStateSpecification extends Specification {

    @Unroll
    def "GetAvailableTokens specification #testNumber"(String testNumber, long requiredAvailableTokens, LocalBucket bucket) {
        setup:
            BucketState state = getState(bucket)
        when:
            long availableTokens = state.getAvailableTokens(bucket.configuration.bandwidths)
        then:
            availableTokens == requiredAvailableTokens
        where:
            [testNumber, requiredAvailableTokens, bucket] << [
                [
                        "#1",
                        10,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)))
                            .build()
                ], [
                        "#2",
                        0,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(0))
                            .build()
                ], [
                        "#3",
                        5,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(5))
                            .build()
                ], [
                        "#4",
                        2,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(5))
                            .addLimit(Bandwidth.simple(2, Duration.ofNanos(100)))
                            .build()
                ], [
                        "#5",
                        10,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .addLimit(Bandwidth.classic(10, Refill.greedy(1, Duration.ofSeconds(1))))
                            .build()
                ]
            ]
    }

    @Unroll
    def "addTokens specification #testNumber"(String testNumber, long tokensToAdd, long requiredAvailableTokens, LocalBucket bucket) {
        setup:
            BucketState state = getState(bucket)
        when:
            state.addTokens(bucket.configuration.bandwidths, tokensToAdd)
            long availableTokens = state.getAvailableTokens(bucket.configuration.bandwidths)
        then:
            availableTokens == requiredAvailableTokens
        where:
            [testNumber, tokensToAdd, requiredAvailableTokens, bucket] << [
                [
                        "#1",
                        10,
                        10,
                        Bucket4j.builder()
                                .withMath(MathType.IEEE_754)
                                .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(0))
                                .build()
                ], [
                        "#2",
                        1,
                        10,
                        Bucket4j.builder()
                                .withMath(MathType.IEEE_754)
                                .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)))
                                .build()
                ], [
                        "#3",
                        6,
                        10,
                        Bucket4j.builder()
                                .withMath(MathType.IEEE_754)
                                .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(5))
                                .build()
                ], [
                        "#4",
                        3,
                        2,
                        Bucket4j.builder()
                                .withMath(MathType.IEEE_754)
                                .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(5))
                                .addLimit(Bandwidth.simple(2, Duration.ofNanos(100)))
                                .build()
                ], [
                        "#5",
                        4,
                        5,
                        Bucket4j.builder()
                                .withMath(MathType.IEEE_754)
                                .addLimit(Bandwidth.classic(10, Refill.greedy(1, Duration.ofSeconds(1))).withInitialTokens(1))
                                .build()
                ]
        ]
    }

    @Unroll
    def "delayAfterWillBePossibleToConsume specification #testNumber"(String testNumber, long toConsume, long requiredTime, LocalBucket bucket) {
            def configuration = bucket.configuration
        TimeMeter timeMeter = bucket.timeMeter
        setup:
            BucketState state = getState(bucket)
        when:
            long actualTime = state.calculateDelayNanosAfterWillBePossibleToConsume(configuration.bandwidths, toConsume, timeMeter.currentTimeNanos())
        then:
            actualTime == requiredTime
        where:
            [testNumber, toConsume, requiredTime, bucket] << [
                [
                        "#1",
                        10,
                        100,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(0))
                            .build()
                ], [
                        "#2",
                        10,
                        100,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofNanos(100))).withInitialTokens(0))
                            .build()
                ], [
                        "#3",
                        10,
                        500,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.classic(10, Refill.greedy(2, Duration.ofNanos(100))).withInitialTokens(0))
                            .build()
                ], [
                        "#4",
                        7,
                        30,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(4))
                            .build()
                ], [
                        "#5",
                        11,
                        70,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(4))
                            .build()
                ], [
                        "#6",
                        3,
                        20,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(1))
                            .addLimit(Bandwidth.simple(5, Duration.ofNanos(10)).withInitialTokens(2))
                            .build()
                ], [
                        "#7",
                        3,
                        20,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.simple(5, Duration.ofNanos(10)).withInitialTokens(2))
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(1))
                            .build()
                ], [
                        "#8",
                        3,
                        0,
                        Bucket4j.builder()
                            .withMath(MathType.IEEE_754)
                            .withCustomTimePrecision(new TimeMeterMock(0))
                            .addLimit(Bandwidth.simple(5, Duration.ofNanos(10)).withInitialTokens(5))
                            .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(3))
                            .build()
                ]
            ]
    }

    @Unroll
    def "calculateFullRefillingTime specification #testNumber"(String testNumber, long requiredTime,
                                                               long timeShiftBeforeAsk, long tokensConsumeBeforeAsk, ConfigurationBuilder builder) {
        setup:
            BucketConfiguration configuration = builder.withMath(MathType.IEEE_754).build()
            BucketState state = BucketState.createInitialState(configuration, 0L)
            state.refillAllBandwidth(configuration.bandwidths, timeShiftBeforeAsk)
            state.consume(configuration.bandwidths, tokensConsumeBeforeAsk)
        when:
            long actualTime = state.calculateFullRefillingTime(configuration.bandwidths, timeShiftBeforeAsk)
        then:
            actualTime == requiredTime
        where:
            [testNumber, requiredTime, timeShiftBeforeAsk, tokensConsumeBeforeAsk, builder] << [
                    [
                            "#1",
                            90,
                            0,
                            0,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(1))
                    ], [
                            "#2",
                            100,
                            0,
                            0,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofNanos(100))).withInitialTokens(1))
                    ], [
                            "#3",
                            1650,
                            0,
                            23,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.classic(10, Refill.greedy(2, Duration.ofNanos(100))).withInitialTokens(0))
                    ], [
                            "#4",
                            1700,
                            0,
                            23,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.classic(10, Refill.intervally(2, Duration.ofNanos(100))).withInitialTokens(0))
                    ], [
                            "#5",
                            60,
                            0,
                            0,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(4))
                    ], [
                            "#6",
                            90,
                            0,
                            0,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(1))
                                    .addLimit(Bandwidth.simple(5, Duration.ofNanos(10)).withInitialTokens(2))
                    ], [
                            "#7",
                            90,
                            0,
                            0,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.simple(5, Duration.ofNanos(10)).withInitialTokens(2))
                                    .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(1))
                    ], [
                            "#8",
                            70,
                            0,
                            0,
                            Bucket4j.configurationBuilder()
                                    .addLimit(Bandwidth.simple(5, Duration.ofNanos(10)).withInitialTokens(5))
                                    .addLimit(Bandwidth.simple(10, Duration.ofNanos(100)).withInitialTokens(3))
                    ]
            ]
    }

    @Unroll
    def "Specification for refill simple bandwidth #n"(int n, long initialTokens, long capacity, long period,
                                                       long initTime, long timeOnRefill, long tokensAfterRefill) {
        setup:
            TimeMeterMock mockTimer = new TimeMeterMock(initTime)
            Bucket bucket = Bucket4j.builder()
                    .withMath(MathType.IEEE_754)
                    .addLimit(Bandwidth.simple(capacity, Duration.ofNanos(period)).withInitialTokens(initialTokens))
                    .withCustomTimePrecision(mockTimer)
                    .build()
            BucketState state = getState(bucket)
            BucketConfiguration configuration = bucket.getConfiguration()
        when:
            mockTimer.setCurrentTimeNanos(timeOnRefill)
            state.refillAllBandwidth(configuration.bandwidths, timeOnRefill)
        then:
            state.getCurrentSize(0) == tokensAfterRefill
            state.getRoundingError(0) == 0
        where:
        n  | initialTokens |    capacity    | period | initTime | timeOnRefill | tokensAfterRefill
        1  |        0      |      1000      | 1000   | 10000    |     10040    |       40
        2  |       50      |      1000      | 1000   | 10000    |     10001    |       51
        3  |       55      |      1000      | 1000   | 10000    |      9999    |       55
        4  |      200      |      1000      | 1000   | 10000    |     20000    |     1000
        5  |        0      |       100      | 1000   | 10000    |     10003    |        0
        6  |       90      |       100      | 1000   | 10000    |     10017    |       91
        7  |        0      |       100      | 1000   | 10000    |     28888    |      100
    }

    @Unroll
    def "Specification for refill classic bandwidth #n"(int n, long initialTokens, long capacity, long refillTokens, long refillPeriod,
                                                       long initTime, long timeOnRefill, long tokensAfterRefill) {
        setup:
            TimeMeterMock mockTimer = new TimeMeterMock(initTime)
            def refill = Refill.greedy(refillTokens, Duration.ofNanos(refillPeriod))
            Bucket bucket = Bucket4j.builder()
                    .withMath(MathType.IEEE_754)
                    .addLimit(Bandwidth.classic(capacity, refill).withInitialTokens(initialTokens))
                    .withCustomTimePrecision(mockTimer)
                    .build()
            BucketState state = getState(bucket)
            BucketConfiguration configuration = bucket.getConfiguration()
        when:
            mockTimer.setCurrentTimeNanos(timeOnRefill)
            state.refillAllBandwidth(configuration.bandwidths, timeOnRefill)
        then:
            state.getCurrentSize(0) == tokensAfterRefill
            state.getRoundingError(0) == 0
        where:
        n  | initialTokens |    capacity    | refillTokens | refillPeriod | initTime | timeOnRefill | tokensAfterRefill
        1  |        0      |      1000      |       1      |          1   | 10000    |     10040    |       40
        2  |       50      |      1000      |      10      |         10   | 10000    |     10001    |       51
        3  |       55      |      1000      |       1      |          1   | 10000    |     10000    |       55
        4  |      200      |      1000      |      10      |         10   | 10000    |     20000    |     1000
        5  |        0      |       100      |       1      |         10   | 10000    |     10003    |        0
        6  |       90      |       100      |       1      |         10   | 10000    |     10017    |       91
        7  |        0      |       100      |       1      |         10   | 10000    |     28888    |      100
    }

    @Unroll
    def "Specification for consume #n"(int n, long initialTokens, long period,
                                       long capacity, long toConsume, long requiredSize
                                       ) {
        setup:
            def bandwidth = Bandwidth.simple(capacity, Duration.ofNanos(period)).withInitialTokens(initialTokens)
            Bucket bucket = Bucket4j.builder()
                .withMath(MathType.IEEE_754)
                .addLimit(bandwidth)
                .build()
            BucketState state = getState(bucket)
            BucketConfiguration configuration = bucket.getConfiguration()
        when:
            state.consume(configuration.bandwidths, toConsume)
        then:
            state.getCurrentSize(0) == requiredSize
        where:
        n  |  initialTokens  | period | capacity | toConsume | requiredSize
        1  |        0        | 1000   |   1000   |    10     |   -10
        2  |       50        | 1000   |   1000   |     2     |    48
        3  |       55        | 1000   |   1000   |   1600    |   -1545
    }

    protected BucketState getState(LocalBucket bucket) {
        return bucket.createSnapshot()
    }

}
