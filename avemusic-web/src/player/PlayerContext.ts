import { createContext } from "react";

import type { SongCard } from "../api/music";

export interface PlayerContextValue {
    queue: SongCard[];
    currentSong: SongCard | null;
    currentIndex: number;
    isPlaying: boolean;
    currentTime: number;
    duration: number;
    volume: number;
    playlistOpen: boolean;
    playbackError: string;

    playQueue(
        songs: SongCard[],
        index: number,
    ): void;

    playAt(
        index: number,
    ): void;

    /**
     * 把歌曲插入当前歌曲之后。
     * 如果当前没有播放队列，则直接播放该歌曲。
     */
    enqueueNext(
        song: SongCard,
    ): void;

    togglePlay(): void;
    previous(): void;
    next(): void;

    seek(
        seconds: number,
    ): void;

    changeVolume(
        volume: number,
    ): void;

    togglePlaylist(): void;
}

export const PlayerContext =
    createContext<
        PlayerContextValue | null
    >(null);
