package com.avemonica.avemusic.music.provider.util;

import java.util.Locale;
import java.util.Set;

public final class ArtistAreaUtil {

    private ArtistAreaUtil() {
    }

    private static final Set<String> CN =
            Set.of(
                    "中国",
                    "中国大陆",
                    "大陆",
                    "华语",
                    "中国香港",
                    "香港",
                    "中国台湾",
                    "台湾",
                    "中国澳门",
                    "澳门",
                    "china",
                    "hong kong",
                    "taiwan",
                    "macau"
            );

    private static final Set<String> JP =
            Set.of(
                    "日本",
                    "japan",
                    "jp"
            );

    private static final Set<String> KR =
            Set.of(
                    "韩国",
                    "南韩",
                    "south korea",
                    "korea",
                    "kr"
            );

    private static final Set<String> EU_US =
            Set.of(
                    "美国",
                    "英国",
                    "法国",
                    "德国",
                    "意大利",
                    "西班牙",
                    "加拿大",
                    "澳大利亚",
                    "新西兰",
                    "荷兰",
                    "瑞典",
                    "挪威",
                    "芬兰",

                    "usa",
                    "united states",
                    "uk",
                    "united kingdom",
                    "france",
                    "germany",
                    "italy",
                    "spain",
                    "canada",
                    "australia",
                    "new zealand",
                    "netherlands",
                    "sweden",
                    "norway",
                    "finland"
            );

    public static String resolve(
            String countryRegion
    ) {
        if (
                countryRegion == null
                        || countryRegion.isBlank()
        ) {
            return "OTHER";
        }

        String value =
                countryRegion
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (CN.contains(value)) {
            return "CN";
        }

        if (JP.contains(value)) {
            return "JP";
        }

        if (KR.contains(value)) {
            return "KR";
        }

        if (EU_US.contains(value)) {
            return "EU_US";
        }

        return "OTHER";
    }
}