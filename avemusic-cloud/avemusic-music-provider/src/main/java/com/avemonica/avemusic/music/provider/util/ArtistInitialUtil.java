package com.avemonica.avemusic.music.provider.util;

import net.sourceforge.pinyin4j.PinyinHelper;

public final class ArtistInitialUtil {

    private ArtistInitialUtil() {
    }

    /**
     * 英文：首字母大写。
     * 中文：取第一个汉字拼音首字母。
     * 其他字符：#。
     */
    public static String resolve(
            String name
    ) {
        if (name == null
                || name.isBlank()) {
            return "#";
        }

        char first = name.trim().charAt(0);

        if (first >= 'A' && first <= 'Z') {
            return String.valueOf(first);
        }

        if (first >= 'a' && first <= 'z') {
            return String.valueOf(
                    Character.toUpperCase(first)
            );
        }

        if (isChinese(first)) {
            String[] values =
                    PinyinHelper
                            .toHanyuPinyinStringArray(
                                    first
                            );

            if (values != null
                    && values.length > 0
                    && !values[0].isEmpty()) {
                char initial =
                        Character.toUpperCase(
                                values[0].charAt(0)
                        );

                if (initial >= 'A'
                        && initial <= 'Z') {
                    return String.valueOf(initial);
                }
            }
        }

        return "#";
    }

    private static boolean isChinese(
            char value
    ) {
        Character.UnicodeBlock block =
                Character.UnicodeBlock.of(value);

        return block
                == Character.UnicodeBlock
                .CJK_UNIFIED_IDEOGRAPHS
                || block
                == Character.UnicodeBlock
                .CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block
                == Character.UnicodeBlock
                .CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block
                == Character.UnicodeBlock
                .CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
