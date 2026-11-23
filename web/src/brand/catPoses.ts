export const CAT_POSES = ['asleep', 'playing', 'peeking', 'pleased'] as const
export type CatPose = (typeof CAT_POSES)[number]
