package com.fedex.api

import com.fedex.api.aggregate.util.BulkDike
import com.fedex.api.client.FedexClient.{FedexClient, FedexClientEnv}

package object aggregate {
  type BulkDikeType[I, A] = BulkDike[FedexClient with FedexClientEnv, Nothing, I, A]
}
