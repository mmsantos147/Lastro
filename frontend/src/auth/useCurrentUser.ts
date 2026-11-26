import { useQuery } from '@tanstack/react-query'
import { currentUserQueryKey, fetchCurrentUser } from './api'

export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: fetchCurrentUser,
    retry: false,
    staleTime: 5 * 60 * 1000,
  })
}
