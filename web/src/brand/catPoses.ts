/** Only poses the owner approved ship; playing, peeking and pleased wait for a better drawing (#101). */
export const CAT_POSES = ['asleep'] as const
export type CatPose = (typeof CAT_POSES)[number]
